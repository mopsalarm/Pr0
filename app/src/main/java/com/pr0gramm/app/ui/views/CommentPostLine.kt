package com.pr0gramm.app.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.pr0gramm.app.R
import com.pr0gramm.app.services.UserSuggestionService
import com.pr0gramm.app.ui.LineMultiAutoCompleteTextView
import com.pr0gramm.app.ui.UsernameAutoCompleteAdapter
import com.pr0gramm.app.ui.UsernameTokenizer
import com.pr0gramm.app.util.*
import kotterknife.bindView

typealias OnSendCommentListener = (text: String) -> Unit

/**
 */
class CommentPostLine @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), InjectorViewMixin {

    private val postButton: View by bindView(R.id.comment_post)
    private val commentTextView: LineMultiAutoCompleteTextView by bindView(R.id.comment_text)

    private val userSuggestionService: UserSuggestionService by instance()

    var onPostCommentClicked: OnSendCommentListener? = null

    init {
        layoutInflater.inflate(R.layout.write_comment_layout, this)
        configureUsernameAutoComplete()

        postButton.setOnClickListener {
            val text = commentTextView.text.trim().toString()
            if (text.isNotEmpty()) {
                onPostCommentClicked?.invoke(text)
            }
        }
    }

    private fun configureUsernameAutoComplete() {
        // change the anchorViews id so it is unique in the view hierarchy
        val anchorView = find<View>(R.id.auto_complete_popup_anchor)
        anchorView.id = ViewUtility.generateViewId()

        commentTextView.setAnchorView(anchorView)
        commentTextView.setTokenizer(UsernameTokenizer())
        commentTextView.setAdapter(UsernameAutoCompleteAdapter(userSuggestionService, context,
                android.R.layout.simple_dropdown_item_1line))

        commentTextView.addTextChangedListener { text ->
            // The post button is only enabled if we have at least one letter.
            postButton.isEnabled = text.trim().isNotEmpty()
        }
    }

    fun clear() {
        commentTextView.setText("")
    }

    fun updateItemId(itemId: Long) {
        TextViewCache.addCaching(this.commentTextView, "post:comment:$itemId")
    }
}
