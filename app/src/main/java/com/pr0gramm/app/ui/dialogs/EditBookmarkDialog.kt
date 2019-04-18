package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.core.os.bundleOf
import com.pr0gramm.app.R
import com.pr0gramm.app.model.bookmark.Bookmark
import com.pr0gramm.app.services.BookmarkService
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.base.bindView
import com.pr0gramm.app.ui.bottomSheet
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.fragmentArgument

class EditBookmarkDialog : BaseDialogFragment("EditBookmarkDialog") {
    private val bookmarkService: BookmarkService by instance()

    private val bookmarkTitle by fragmentArgument<String>("Bookmark")
    private val bookmarkTitleView: EditText by bindView(R.id.bookmark_name)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return bottomSheet(requireContext()) {
            title(R.string.bookmark_editor_title)

            layout(R.layout.bookmark_edit)

            negative(R.string.delete) {
                deleteClicked()
            }

            positive(R.string.okay) {
                okayClicked()
            }
        }
    }

    private fun okayClicked() {
        val newTitle = bookmarkTitleView.text.toString().take(64).trim()
        if (newTitle != bookmarkTitle) {
            renameTo(newTitle)
        }

        dismiss()
    }

    private fun renameTo(newTitle: String) {
        val bookmark = bookmarkService.byTitle(bookmarkTitle)
        if (bookmark != null) {
            bookmarkService.rename(bookmark, newTitle)
        }
    }

    private fun deleteClicked() {
        val bookmark = bookmarkService.byTitle(bookmarkTitle)
        if (bookmark != null) {
            bookmarkService.delete(bookmark)
        }

        dismiss()
    }

    override suspend fun onDialogViewCreated() {
        bookmarkTitleView.setText(bookmarkTitle)
    }

    companion object {
        fun forBookmark(b: Bookmark): EditBookmarkDialog {
            return EditBookmarkDialog().apply {
                arguments = bundleOf("Bookmark" to b.title)
            }
        }
    }
}
