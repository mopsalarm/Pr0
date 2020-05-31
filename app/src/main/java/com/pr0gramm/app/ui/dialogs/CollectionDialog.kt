package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import com.pr0gramm.app.R
import com.pr0gramm.app.services.*
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.base.launchUntilDestroy
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.instance
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class CollectionDialog : BaseDialogFragment("CollectionDialog") {
    private val collectionsService: CollectionsService by instance()
    private val collectionItemService: CollectionItemsService by instance()
    private val userService: UserService by instance()

    private val editCollectionId: Long? by optionalFragmentArgument(name = "editCollectionId")

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // lookup collection to edit if it exists.
        val editCollection: PostCollection? = editCollectionId?.let { collectionsService.byId(it) }

        return dialog(requireContext()) {
            title(if (editCollection != null) "Edit collection" else "New collection")
            layout(R.layout.dialog_collection_create)
            positive("Save")
            negative { dismissAllowingStateLoss() }

            if (editCollection != null) {
                neutral("Delete") { deleteCollection(editCollection.id) }
            }

            noAutoDismiss()

            onShow { dialog -> configureDialog(dialog, editCollection) }
        }
    }

    private fun configureDialog(dialog: Dialog, editCollection: PostCollection?) {
        val spinner = dialog.find<Spinner>(R.id.privacy_options)
        spinner.adapter = PrivacySpinnerAdapter()
        spinner.setSelection(if (editCollection?.isPublic == true) 1 else 0)

        val buttonView: Button = dialog.find(android.R.id.button1)
        buttonView.isEnabled = editCollection != null


        val nameView = dialog.find<TextView>(R.id.name)
        nameView.text = editCollection?.title ?: ""

        nameView.addTextChangedListener { changedText ->
            val name = changedText.toString().trim()
            val isValidName = collectionsService.isValidNameForNewCollection(name)
            buttonView.isEnabled = isValidName
        }


        val defaultCollectionView: CompoundButton = dialog.find(R.id.default_collection)
        defaultCollectionView.isChecked = editCollection?.isDefault ?: collectionsService.defaultCollection == null
        defaultCollectionView.isEnabled = userService.userIsPremium

        buttonView.setOnClickListener {
            val name = nameView.text.toString().trim()
            val isPublic = spinner.selectedItemId == 1L
            val isDefault = defaultCollectionView.isChecked

            if (editCollection == null) {
                createCollection(name, isPublic, isDefault)
            } else {
                updateCollection(editCollection.id, name, isPublic, isDefault)
            }
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
            0 -> Pair("Private", R.drawable.ic_collection_private)
            else -> Pair("Public", R.drawable.ic_collection_public)
        }

        view.text = text
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
