package com.pr0gramm.app.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.github.salomonbrys.kodein.instance
import com.jakewharton.rxbinding.view.clicks
import com.jakewharton.rxbinding.widget.afterTextChangeEvents
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.LineMultiAutoCompleteTextView
import com.pr0gramm.app.ui.UsernameAutoCompleteAdapter
import com.pr0gramm.app.ui.UsernameTokenizer
import com.pr0gramm.app.util.ViewUtility
import com.pr0gramm.app.util.layoutInflater
import kotterknife.bindView
import rx.Observable

/**
 */
class CommentPostLine @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), KodeinViewMixin {

    private val postButton: View by bindView(R.id.comment_post)
    private val commentTextView: LineMultiAutoCompleteTextView by bindView(R.id.comment_text)

    init {
        layoutInflater.inflate(R.layout.write_comment_layout, this)

        // change the anchorViews id so it is unique in the view hierarchy
        val anchorView = findViewById(R.id.auto_complete_popup_anchor)
        anchorView.id = ViewUtility.generateViewId()

        commentTextView.setAnchorView(anchorView)
        commentTextView.setTokenizer(UsernameTokenizer())
        commentTextView.setAdapter(UsernameAutoCompleteAdapter(instance(), context,
                android.R.layout.simple_dropdown_item_1line))

        // The post button is only enabled if we have at least one letter.
        commentTextView.afterTextChangeEvents()
                .map { it.editable().toString().trim().isNotEmpty() }
                .subscribe { postButton.isEnabled = it }
    }

    /**
     * Observable filled with the comments each time the user clicks on the button
     */
    fun comments(): Observable<String> {
        return postButton.clicks()
                .map { commentTextView.text.toString().trim() }
                .filter { text -> !text.isEmpty() }
    }

    /**
     * Notified about every text changes after typing
     */
    fun textChanges(): Observable<String> {
        return commentTextView.afterTextChangeEvents()
                .map { event -> event.editable().toString() }
    }

    fun clear() {
        commentTextView.setText("")
    }

    fun setCommentDraft(text: String) {
        this.commentTextView.setText(text)
    }
}
