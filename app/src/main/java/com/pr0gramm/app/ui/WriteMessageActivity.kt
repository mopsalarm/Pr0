package com.pr0gramm.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import com.github.salomonbrys.kodein.instance
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.MessageConverter
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.parcel.MessageParceler
import com.pr0gramm.app.parcel.NewCommentParceler
import com.pr0gramm.app.parcel.core.Parceler
import com.pr0gramm.app.services.*
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.fragments.BusyDialog
import com.pr0gramm.app.util.decoupleSubscribe
import com.pr0gramm.app.util.visible
import kotterknife.bindView
import java.util.*

/**
 */
class WriteMessageActivity : BaseAppCompatActivity("WriteMessageActivity") {
    private val inboxService: InboxService by instance()
    private val userService: UserService by instance()
    private val voteService: VoteService by instance()
    private val suggestionService: UserSuggestionService by instance()

    private val buttonSubmit: Button by bindView(R.id.submit)
    private val messageText: LineMultiAutoCompleteTextView by bindView(R.id.new_message_text)
    private val messageView: MessageView by bindView(R.id.message_view)

    private val receiverName: String by lazy { intent.getStringExtra(ARGUMENT_RECEIVER_NAME) }
    private val receiverId: Long by lazy { intent.getLongExtra(ARGUMENT_RECEIVER_ID, 0) }
    private val isCommentAnswer: Boolean by lazy { intent.hasExtra(ARGUMENT_COMMENT_ID) }
    private val parentCommentId: Long by lazy { intent.getLongExtra(ARGUMENT_COMMENT_ID, 0) }
    private val itemId: Long by lazy { intent.getLongExtra(ARGUMENT_ITEM_ID, 0) }

    public override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.basic)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.fragment_write_message)

        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        // set title
        title = getString(R.string.write_message_title, receiverName)

        // and previous message
        updateMessageView()

        buttonSubmit.setOnClickListener { sendMessageNow() }

        messageText.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                val empty = s.toString().trim().isEmpty()
                buttonSubmit.isEnabled = !empty
                supportInvalidateOptionsMenu()

                // cache to restore it later.
                CACHE.put(messageCacheKey, s.toString())
            }
        })

        messageText.setTokenizer(UsernameTokenizer())
        messageText.setAdapter(UsernameAutoCompleteAdapter(suggestionService, this,
                android.R.layout.simple_dropdown_item_1line, "@"))

        messageText.setAnchorView(findViewById(R.id.auto_complete_popup_anchor))

        // restore cached text.
        val cached = CACHE[messageCacheKey]
        if (cached != null) {
            messageText.setText(cached)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return OptionMenuHelper.dispatch(this, item)
    }

    @OnOptionsItemSelected(android.R.id.home)
    override fun finish() {
        super.finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_write_message, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.action_send)?.isEnabled = buttonSubmit.isEnabled
        return true
    }

    private fun finishAfterSending() {
        CACHE.remove(messageCacheKey)
        finish()
    }

    @OnOptionsItemSelected(R.id.action_send)
    fun sendMessageNow() {
        val message = getMessageText()
        if (message.isEmpty()) {
            showDialog(this) {
                content(R.string.message_must_not_be_empty)
                positive()
            }

            return
        }

        if (isCommentAnswer) {
            val parentComment = parentCommentId
            val itemId = itemId

            voteService.postComment(itemId, parentComment, message)
                    .decoupleSubscribe()
                    .compose(bindToLifecycleAsync())
                    .lift(BusyDialog.busyDialog(this))
                    .doOnCompleted { this.finishAfterSending() }
                    .subscribeWithErrorHandling { newComments ->
                        val result = Intent()
                        result.putExtra(RESULT_EXTRA_NEW_COMMENT, NewCommentParceler(newComments))
                        setResult(Activity.RESULT_OK, result)
                    }

            Track.writeComment()

        } else {
            // now send message
            inboxService.send(receiverId, message)
                    .decoupleSubscribe()
                    .compose(bindToLifecycleAsync<Any>())
                    .lift(BusyDialog.busyDialog<Any>(this))
                    .doOnCompleted { finishAfterSending() }
                    .subscribeWithErrorHandling()

            Track.writeMessage()
        }
    }

    internal fun getMessageText(): String {
        return messageText.text.toString().trim()
    }

    private fun updateMessageView() {
        // hide view by default and only show, if we found data
        messageView.visible = false

        val extras = intent?.extras ?: return

        val message = Parceler.get(MessageParceler::class.java, extras, ARGUMENT_MESSAGE)
        if (message != null) {
            messageView.update(message, userService.name)
            messageView.visible = true
        }
    }

    internal val messageCacheKey: String
        get() {
            if (isCommentAnswer) {
                return itemId.toString() + "-" + parentCommentId
            } else {
                return "msg-" + receiverId
            }
        }

    companion object {
        private const val ARGUMENT_MESSAGE = "WriteMessageFragment.message"
        private const val ARGUMENT_RECEIVER_ID = "WriteMessageFragment.userId"
        private const val ARGUMENT_RECEIVER_NAME = "WriteMessageFragment.userName"
        private const val ARGUMENT_COMMENT_ID = "WriteMessageFragment.commentId"
        private const val ARGUMENT_ITEM_ID = "WriteMessageFragment.itemId"
        private const val RESULT_EXTRA_NEW_COMMENT = "WriteMessageFragment.result.newComment"

        private var CACHE: MutableMap<String, String> = HashMap()

        fun intent(context: Context, message: Api.Message): Intent {
            val intent = intent(context, message.senderId.toLong(), message.name)
            intent.putExtra(ARGUMENT_MESSAGE, MessageParceler(message))
            return intent
        }

        fun intent(context: Context, userId: Long, userName: String): Intent {
            val intent = Intent(context, WriteMessageActivity::class.java)
            intent.putExtra(ARGUMENT_RECEIVER_ID, userId)
            intent.putExtra(ARGUMENT_RECEIVER_NAME, userName)
            return intent
        }

        fun answerToComment(context: Context, feedItem: FeedItem, comment: Api.Comment): Intent {
            return answerToComment(context, MessageConverter.of(feedItem, comment))
        }

        fun answerToComment(context: Context, message: Api.Message): Intent {
            val itemId = message.itemId
            val commentId = message.id

            val intent = intent(context, message)
            intent.putExtra(ARGUMENT_COMMENT_ID, commentId)
            intent.putExtra(ARGUMENT_ITEM_ID, itemId)
            return intent
        }

        fun getNewComment(data: Intent): Api.NewComment {
            return Parceler.get(NewCommentParceler::class.java, data.extras, RESULT_EXTRA_NEW_COMMENT)!!
        }
    }
}
