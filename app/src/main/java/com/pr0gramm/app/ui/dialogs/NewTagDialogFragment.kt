package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.view.View
import androidx.core.view.isVisible
import com.pr0gramm.app.R
import com.pr0gramm.app.databinding.DialogAddTagsBinding
import com.pr0gramm.app.ui.TagSuggestionService
import com.pr0gramm.app.ui.base.ViewBindingDialogFragment
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.addTextChangedListener
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.hideSoftKeyboard

/**
 */
class NewTagDialogFragment :
        ViewBindingDialogFragment<DialogAddTagsBinding>("NewTagDialogFragment", DialogAddTagsBinding::inflate) {

    private val tagSuggestions: TagSuggestionService by instance()

    override fun onCreateDialog(contentView: View): Dialog {
        return dialog(this) {
            contentView(contentView)
            negative(R.string.cancel) { onCancelClicked() }
            positive(R.string.dialog_action_add) { onOkayClicked() }

            onShow {
                AndroidUtility.showSoftKeyboard(views.tagInput)
            }
        }
    }

    override fun onDialogViewCreated() {
        tagSuggestions.setupView(views.tagInput)

        views.tagInput.addTextChangedListener { text ->
            views.opinionHint.isVisible = tagSuggestions.containsQuestionableTag(text)
        }
    }

    private fun onOkayClicked() {
        val text = views.tagInput.text.toString()

        // split text into tags.
        val tags = text.split(',', '#').map { it.trim() }.filter { it.isNotEmpty() }

        // do nothing if the user had not typed any tags
        if (tags.isEmpty())
            return

        // inform parent
        (parentFragment as OnAddNewTagsListener).onNewTags(tags)

        hideSoftKeyboard()
    }

    private fun onCancelClicked() {
        hideSoftKeyboard()
    }

    /**
     * The parent fragment must implement this interface.
     * It will be informed by this class if the user added tags.
     */
    interface OnAddNewTagsListener {
        /**
         * Called when the dialog finishes with new tags.
         */
        fun onNewTags(tags: List<String>)
    }
}
