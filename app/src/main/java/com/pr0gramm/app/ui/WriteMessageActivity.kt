package com.pr0gramm.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.text.SpannableStringBuilder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.core.text.bold
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.Message
import com.pr0gramm.app.api.pr0gramm.MessageConverter
import com.pr0gramm.app.databinding.FragmentWriteMessageBinding
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.parcel.*
import com.pr0gramm.app.services.*
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.bindViews
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.base.withViewDisabled
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.instance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 */
class WriteMessageActivity : BaseAppCompatActivity("WriteMessageActivity") {
    private val inboxService: InboxService by instance()
    private val userService: UserService by instance()
    private val voteService: VoteService by instance()
    private val suggestionService: UserSuggestionService by instance()

    private val views by bindViews(FragmentWriteMessageBinding::inflate)

    private val receiverName: String by lazy { intent.getStringExtra(ARGUMENT_RECEIVER_NAME)!! }
    private val receiverId: Long by lazy { intent.getLongExtra(ARGUMENT_RECEIVER_ID, 0) }
    private val isCommentAnswer: Boolean by lazy { intent.hasExtra(ARGUMENT_COMMENT_ID) }
    private val parentCommentId: Long by lazy { intent.getLongExtra(ARGUMENT_COMMENT_ID, 0) }
    private val itemId: Long by lazy { intent.getLongExtra(ARGUMENT_ITEM_ID, 0) }
    private val titleOverride: String? by lazy { intent.getStringExtra(ARGUMENT_TITLE) }

    private val parentComments: List<ParentComment> by lazy {
        intent.getParcelableExtra<ParentComments>(ARGUMENT_EXCERPTS)?.comments ?: listOf()
    }

    private var selectedUsers by observeChangeEx(setOf<String>()) { _, _ -> updateViewState() }

    public override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.basic)
        super.onCreate(savedInstanceState)

        setContentView(views)

        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        // set title
        title = titleOverride ?: getString(R.string.write_message_title, receiverName)

        // and previous message
        updateMessageView()

        views.submit.setOnClickListener { sendMessageNow() }

        views.newMessageText.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                val tooShort = s.toString().trim().length < 3
                views.submit.isEnabled = !tooShort
                invalidateOptionsMenu()
            }
        })

        val cacheKey = if (isCommentAnswer) "$itemId-$parentCommentId" else "msg-$receiverId"
        TextViewCache.addCaching(views.newMessageText, cacheKey)

        views.newMessageText.setTokenizer(UsernameTokenizer())
        views.newMessageText.setAdapter(UsernameAutoCompleteAdapter(suggestionService, this,
                android.R.layout.simple_dropdown_item_1line, "@"))

        views.newMessageText.setAnchorView(findViewById(R.id.auto_complete_popup_anchor))

        if (isCommentAnswer) {
            views.newMessageText.hint = getString(R.string.comment_hint)
        }

        // only show if we can link to someone else
        val shouldShowParentComments = this.parentComments.any {
            it.user != receiverName && it.user != userService.loginState.name
        }

        if (shouldShowParentComments) {
            // make the views visible
            find<View>(R.id.authors_title).isVisible = true
            views.parentComments.isVisible = true

            views.parentComments.adapter = Adapter()
            views.parentComments.layoutManager = LinearLayoutManager(this)
            views.parentComments.itemAnimator = null

            updateViewState()
        }
    }

    override fun onPostResume() {
        super.onPostResume()

        AndroidUtility.showSoftKeyboard(views.newMessageText)
    }

    private fun updateViewState() {
        val adapter = views.parentComments.adapter as Adapter

        adapter.submitList(parentComments.map { comment ->
            val isReceiverUser = comment.user == receiverName
            val isCurrentUser = comment.user == userService.loginState.name
            SelectedParentComment(comment,
                    enabled = !isReceiverUser && !isCurrentUser,
                    selected = comment.user in selectedUsers)
        })

        // sorted users, or empty if the list has focus
        val sortedUsers = selectedUsers.sorted()
        if (sortedUsers.isEmpty()) {
            views.newMessageText.suffix = null
        } else {
            views.newMessageText.suffix = sortedUsers.joinToString(" ") { "@$it" }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return true == when (item.itemId) {
            android.R.id.home -> finish()
            R.id.action_send -> sendMessageNow()
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun finish() {
        // hide keyboard before closing the activity.
        hideSoftKeyboard()

        super.finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_write_message, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.action_send)?.isEnabled = views.submit.isEnabled
        return true
    }

    private fun finishAfterSending() {
        TextViewCache.invalidate(views.newMessageText)

        finish()
    }

    private fun sendMessageNow() {
        val message = getMessageText()
        if (message.isEmpty()) {
            showDialog(this) {
                content(R.string.message_must_not_be_empty)
                positive()
            }

            return
        }

        if (isCommentAnswer) {
            val itemId = itemId
            val parentComment = parentCommentId

            launchWhenStarted(busyIndicator = true) {
                withViewDisabled(views.submit) {
                    val newComments = withContext(NonCancellable + Dispatchers.Default) {
                        voteService.postComment(itemId, parentComment, message)
                    }

                    val result = Intent()

                    result.putExtra(RESULT_EXTRA_NEW_COMMENT, NewCommentParceler(newComments))

                    setResult(Activity.RESULT_OK, result)

                    finishAfterSending()
                }
            }

            Track.writeComment(root = parentCommentId == 0L)

        } else {
            launchWhenStarted(busyIndicator = true) {
                withViewDisabled(views.submit) {
                    withContext(NonCancellable + Dispatchers.Default) {
                        inboxService.send(receiverId, message)
                    }

                    finishAfterSending()
                }
            }

            Track.writeMessage()
        }
    }

    private fun getMessageText(): String {
        return views.newMessageText.text.toString().trim()
    }

    private fun updateMessageView() {
        // hide view by default and only show, if we found data
        views.messageView.isVisible = false

        val extras = intent?.extras ?: return

        val message = extras.getParcelableOrNull<MessageSerializer>(ARGUMENT_MESSAGE)?.message
        if (message != null) {
            views.messageView.update(message, userService.name)
            views.messageView.isVisible = true
        }
    }

    companion object {
        private const val ARGUMENT_MESSAGE = "WriteMessageFragment.message"
        private const val ARGUMENT_RECEIVER_ID = "WriteMessageFragment.userId"
        private const val ARGUMENT_RECEIVER_NAME = "WriteMessageFragment.userName"
        private const val ARGUMENT_COMMENT_ID = "WriteMessageFragment.commentId"
        private const val ARGUMENT_ITEM_ID = "WriteMessageFragment.itemId"
        private const val ARGUMENT_EXCERPTS = "WriteMessageFragment.excerpts"
        private const val ARGUMENT_TITLE = "WriteMessageFragment.title"

        private const val RESULT_EXTRA_NEW_COMMENT = "WriteMessageFragment.result.newComment"

        fun intent(context: Context, message: Message): Intent {
            return activityIntent<WriteMessageActivity>(context) {
                putExtra(ARGUMENT_RECEIVER_ID, message.senderId.toLong())
                putExtra(ARGUMENT_RECEIVER_NAME, message.name)
                putExtra(ARGUMENT_MESSAGE, MessageSerializer(message))
            }
        }

        fun intent(context: Context, userId: Long, userName: String): Intent {
            return activityIntent<WriteMessageActivity>(context) {
                putExtra(ARGUMENT_RECEIVER_ID, userId)
                putExtra(ARGUMENT_RECEIVER_NAME, userName)
            }
        }

        fun newComment(context: Context, item: FeedItem): Intent {
            return activityIntent<WriteMessageActivity>(context) {
                putExtra(ARGUMENT_ITEM_ID, item.id)
                putExtra(ARGUMENT_COMMENT_ID, 0L)
                putExtra(ARGUMENT_TITLE, context.getString(R.string.write_comment, item.user))
            }
        }

        fun answerToComment(
                context: Context, feedItem: FeedItem, comment: Api.Comment,
                parentComments: List<ParentComment>): Intent {

            return answerToComment(context, MessageConverter.of(feedItem, comment), parentComments)
        }

        fun answerToComment(
                context: Context, message: Message,
                parentComments: List<ParentComment> = listOf()): Intent {

            val itemId = message.itemId
            val commentId = message.id

            return intent(context, message).apply {
                putExtra(ARGUMENT_ITEM_ID, itemId)
                putExtra(ARGUMENT_COMMENT_ID, commentId)
                putExtra(ARGUMENT_EXCERPTS, ParentComments(parentComments))
            }
        }

        fun getNewCommentFromActivityResult(data: Intent): Api.NewComment {
            return data.extras
                    ?.getParcelableOrNull<NewCommentParceler>(RESULT_EXTRA_NEW_COMMENT)
                    ?.value
                    ?: throw IllegalArgumentException("no comment found in Intent")
        }
    }

    data class ParentComment(val user: String, val excerpt: String) {
        companion object {
            fun ofComment(comment: Api.Comment): ParentComment {
                val cleaned = comment.content.replace("\\s+".toRegex(), " ")
                val content = if (cleaned.length < 120) cleaned else cleaned.take(120) + "…"
                return ParentComment(comment.name, content)
            }
        }
    }

    private data class SelectedParentComment(
            val comment: ParentComment, val enabled: Boolean, val selected: Boolean)

    private inner class Adapter : AsyncListAdapter<SelectedParentComment, ViewHolder>(ItemCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = parent.layoutInflater.inflate(R.layout.row_comment_excerpt, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.update(getItem(position))
        }
    }

    private class ItemCallback : DiffUtil.ItemCallback<SelectedParentComment>() {
        override fun areItemsTheSame(oldItem: SelectedParentComment, newItem: SelectedParentComment): Boolean {
            return oldItem.comment === newItem.comment
        }

        override fun areContentsTheSame(oldItem: SelectedParentComment, newItem: SelectedParentComment): Boolean {
            return oldItem == newItem
        }
    }

    private inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val excerptTextView: CheckedTextView = itemView as CheckedTextView

        fun update(parentComment: SelectedParentComment) {
            excerptTextView.isChecked = parentComment.selected
            excerptTextView.isEnabled = parentComment.enabled

            excerptTextView.text = SpannableStringBuilder().apply {
                bold {
                    append(parentComment.comment.user)
                }

                append(" ")
                append(parentComment.comment.excerpt)
            }

            excerptTextView.setOnClickListener {
                if (excerptTextView.isEnabled) {
                    val user = parentComment.comment.user
                    if (user in selectedUsers) {
                        selectedUsers = selectedUsers - user
                    } else {
                        selectedUsers = selectedUsers + user
                    }
                }
            }
        }
    }

    class ParentComments(val comments: List<ParentComment>) : DefaultParcelable {
        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeValues(comments) { comment ->
                writeString(comment.user)
                writeString(comment.excerpt)
            }
        }

        companion object CREATOR : SimpleCreator<ParentComments>(javaClassOf()) {
            override fun createFromParcel(source: Parcel): ParentComments {
                val comments = source.readValues {
                    ParentComment(
                            user = source.readStringNotNull(),
                            excerpt = source.readStringNotNull(),
                    )
                }

                return ParentComments(comments)
            }
        }
    }
}
