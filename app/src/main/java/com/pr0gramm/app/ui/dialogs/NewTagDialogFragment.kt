package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.MultiAutoCompleteTextView
import androidx.core.view.isVisible
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.TagSuggestionService
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.base.bindView
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.addTextChangedListener
import com.pr0gramm.app.util.di.instance

/**
 */
class NewTagDialogFragment : BaseDialogFragment("NewTagDialogFragment") {
    private val tagInput: MultiAutoCompleteTextView by bindView(R.id.tag)
    private val opinionHint: View by bindView(R.id.opinion_hint)

    private val tagSuggestions: TagSuggestionService by instance()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return dialog(this) {
            layout(R.layout.dialog_add_tags)
            negative(R.string.cancel) { AndroidUtility.hideSoftKeyboard(tagInput) }
            positive(R.string.dialog_action_add) { onOkayClicked() }
            cancelable()
        }
    }

    override suspend fun onDialogViewCreated() {
        tagSuggestions.setupView(tagInput)

        tagInput.addTextChangedListener { text ->
            opinionHint.isVisible = tagSuggestions.containsQuestionableTag(text)
        }
    }

    private fun onOkayClicked() {
        val text = tagInput.text.toString()

        // split text into tags.
        val tags = text.split(',', '#').map { it.trim() }.filter { it.isNotEmpty() }

        // do nothing if the user had not typed any tags
        if (tags.isEmpty())
            return

        // inform parent
        (parentFragment as OnAddNewTagsListener).onAddNewTags(tags)

        AndroidUtility.hideSoftKeyboard(tagInput)
    }

    /**
     * The parent fragment must implement this interface.
     * It will be informed by this class if the user added tags.
     */
    interface OnAddNewTagsListener {
        /**
         * Called when the dialog finishes with new tags.
         */
        fun onAddNewTags(tags: List<String>)
    }
}
