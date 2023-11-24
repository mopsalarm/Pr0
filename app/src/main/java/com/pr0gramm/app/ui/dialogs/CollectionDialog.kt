package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import com.pr0gramm.app.R
import com.pr0gramm.app.databinding.DialogCollectionCreateBinding
import com.pr0gramm.app.services.CollectionItemsService
import com.pr0gramm.app.services.CollectionsService
import com.pr0gramm.app.services.PostCollection
import com.pr0gramm.app.services.Result
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.base.ViewBindingDialogFragment
import com.pr0gramm.app.ui.base.launchUntilDestroy
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.ui.showDialog
import com.pr0gramm.app.util.arguments
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.dp
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.inflateDetachedChild
import com.pr0gramm.app.util.optionalFragmentArgument
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class CollectionDialog : ViewBindingDialogFragment<DialogCollectionCreateBinding>("CollectionDialog", DialogCollectionCreateBinding::inflate) {

    private val collectionsService: CollectionsService by instance()
    private val collectionItemService: CollectionItemsService by instance()
    private val userService: UserService by instance()

    private val editCollectionId: Long? by optionalFragmentArgument(name = "editCollectionId")

    override fun onCreateDialog(contentView: View): Dialog {
        // lookup collection to edit if it exists.
        val editCollection: PostCollection? = editCollectionId?.let { collectionsService.byId(it) }

        return dialog(requireContext()) {
            title(if (editCollection != null) R.string.collection_edit else R.string.collection_new)

            positive(R.string.action_save)
            negative { dismissAllowingStateLoss() }

            contentView(contentView)

            if (editCollection != null) {
                neutral(R.string.action_delete) { askToDeleteCollection(editCollection.id) }
            }

            noAutoDismiss()

            onShow { dialog -> configureDialog(dialog, editCollection) }
        }
    }

    private fun configureDialog(dialog: Dialog, editCollection: PostCollection?) {
        views.privacyOptions.adapter = PrivacySpinnerAdapter()
        views.privacyOptions.setSelection(if (editCollection?.isPublic == true) 1 else 0)

        val buttonView: Button = dialog.find(android.R.id.button1)
        buttonView.isEnabled = editCollection != null

        views.name.setText(editCollection?.title ?: "")

        views.name.addTextChangedListener { changedText ->
            val name = changedText.toString().trim()
            val isValidName = collectionsService.isValidNameForNewCollection(name)
            buttonView.isEnabled = isValidName
        }


        views.defaultCollection.isChecked = editCollection?.isDefault ?: collectionsService.defaultCollection == null
        views.defaultCollection.isEnabled = userService.userIsPremium

        buttonView.setOnClickListener {
            val name = views.name.text.toString().trim()
            val isPublic = views.privacyOptions.selectedItemId == 1L
            val isDefault = views.defaultCollection.isChecked

            if (editCollection == null) {
                createCollection(name, isPublic, isDefault)
            } else {
                updateCollection(editCollection.id, name, isPublic, isDefault)
            }
        }
    }

    private fun askToDeleteCollection(id: Long) {
        showDialog(this) {
            content(R.string.collection_delete_confirm)

            positive(R.string.action_delete) {
                deleteCollection(id)
            }

            negative(R.string.cancel)
        }
    }

    private fun deleteCollection(collectionId: Long) {
        launchUntilDestroy(busyIndicator = true) {
            withContext(NonCancellable) {
                val result = collectionsService.delete(collectionId)
                if (result is Result.Success) {
                    collectionItemService.deleteCollection(collectionId)
                }
            }

            dismissAllowingStateLoss()
        }
    }

    private fun createCollection(name: String, public: Boolean, default: Boolean) {
        launchUntilDestroy(busyIndicator = true) {
            withContext(NonCancellable) {
                logger.info { "Create collection with the name: '$name' public=$public, default=$default" }
                collectionsService.create(name, public, default)
            }

            dismissAllowingStateLoss()
        }
    }

    private fun updateCollection(id: Long, name: String, public: Boolean, default: Boolean) {
        launchUntilDestroy(busyIndicator = true) {
            withContext(NonCancellable) {
                logger.info { "Edit collection $id: name='$name', public=$public, default=$default" }
                collectionsService.edit(id, name, public, default)
            }

            dismissAllowingStateLoss()
        }
    }

    companion object {
        fun newInstance(collectionToEdit: PostCollection? = null): CollectionDialog {
            return CollectionDialog().arguments {
                if (collectionToEdit != null) {
                    putLong("editCollectionId", collectionToEdit.id)
                }
            }
        }
    }
}

private class PrivacySpinnerAdapter : BaseAdapter() {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: TextView = parent.inflateDetachedChild(R.layout.row_collection_privacy)

        val (text, icon) = when (position) {
            0 -> Pair(R.string.collection_prive, R.drawable.ic_collection_private)
            else -> Pair(R.string.collection_public, R.drawable.ic_collection_public)
        }

        view.setText(text)
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getView(position, convertView, parent).apply {
            updatePadding(left = context.dp(16), right = context.dp(16))
            minimumHeight = context.dp(48)
        }
    }

    override fun getItem(position: Int): Any {
        return position
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return 2
    }
}
