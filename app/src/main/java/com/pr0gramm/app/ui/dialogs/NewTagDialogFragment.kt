package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.MultiAutoCompleteTextView
import com.google.common.base.Splitter
import com.jakewharton.rxbinding.widget.textChanges
import com.pr0gramm.app.R
import com.pr0gramm.app.services.config.Config
import com.pr0gramm.app.ui.TagInputView
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.bottomSheet
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.visible
import org.kodein.di.erased.instance

/**
 */
class NewTagDialogFragment : BaseDialogFragment("NewTagDialogFragment") {
    private val tagInput: MultiAutoCompleteTextView by bindView(R.id.tag)
    private val opinionHint: View by bindView(R.id.opinion_hint)

    private val config: Config by instance()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return bottomSheet(this) {
            layout(R.layout.dialog_add_tags)
            negative(R.string.cancel) { AndroidUtility.hideSoftKeyboard(tagInput) }
            positive(R.string.dialog_action_add) { onOkayClicked() }
            cancelable()
        }
    }

    override fun onDialogViewCreated() {
        TagInputView.setup(tagInput)

        tagInput.textChanges().subscribe { text ->
            val lower = text.toString().toLowerCase()
            opinionHint.visible = config.questionableTags.any { lower.contains(it) }
        }
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
