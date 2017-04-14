package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.widget.MultiAutoCompleteTextView
import com.google.common.base.Splitter
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.TagInputView
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.util.AndroidUtility
import kotterknife.bindView

/**
 */
class NewTagDialogFragment : BaseDialogFragment() {
    private val tagInput: MultiAutoCompleteTextView by bindView(R.id.tag)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return dialog(activity) {
            layout(R.layout.dialog_add_tags)
            negative(R.string.cancel) { AndroidUtility.hideSoftKeyboard(tagInput) }
            positive(R.string.dialog_action_add) { onOkayClicked() }
        }
    }

    override fun onDialogViewCreated() {
        TagInputView.setup(tagInput)
    }

    private fun onOkayClicked() {
        val text = tagInput.text.toString()

        // split text into tags.
        val tags = Splitter.on(",").omitEmptyStrings().trimResults().splitToList(text)

        // do nothing if the user had not typed any tags
        if (tags.isEmpty())
            return

        // inform parent
        (parentFragment as OnAddNewTagsListener).onAddNewTags(tags)

        AndroidUtility.hideSoftKeyboard(tagInput)
    }

    override fun injectComponent(activityComponent: ActivityComponent) {
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
