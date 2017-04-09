package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.widget.MultiAutoCompleteTextView
import butterknife.BindView
import com.google.common.base.Splitter
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.DialogBuilder
import com.pr0gramm.app.ui.TagInputView
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.util.AndroidUtility

/**
 */
class NewTagDialogFragment : BaseDialogFragment() {
    @BindView(R.id.tag)
    internal var tagInput: MultiAutoCompleteTextView? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return DialogBuilder.start(activity)
                .layout(R.layout.dialog_add_tags)
                .negative(R.string.cancel) { AndroidUtility.hideSoftKeyboard(tagInput) }
                .positive(R.string.dialog_action_add, Runnable { this.onOkayClicked() })
                .build()
    }

    override fun onDialogViewCreated() {
        TagInputView.setup(tagInput)
    }

    private fun onOkayClicked() {
        val text = tagInput!!.text.toString()

        // split text into tags.
        val splitter = Splitter.on(",").omitEmptyStrings().trimResults()
        val tags = splitter.splitToList(text)

        // do nothing if the user had not typed any tags
        if (tags.isEmpty())
            return

        // inform parent
        (parentFragment as OnAddNewTagsListener).onAddNewTags(tags)

        AndroidUtility.hideSoftKeyboard(tagInput)
    }

    override fun injectComponent(activityComponent: ActivityComponent) {
        activityComponent.inject(this)
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
