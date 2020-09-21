package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.content.Intent
import android.view.View
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.databinding.BookmarkEditBinding
import com.pr0gramm.app.model.bookmark.Bookmark
import com.pr0gramm.app.orm.link
import com.pr0gramm.app.orm.migrate
import com.pr0gramm.app.orm.uri
import com.pr0gramm.app.services.BookmarkService
import com.pr0gramm.app.ui.MainActivity
import com.pr0gramm.app.ui.base.ViewBindingDialogFragment
import com.pr0gramm.app.ui.bottomSheet
import com.pr0gramm.app.util.activityIntent
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.fragmentArgument
import okio.ByteString.Companion.encodeUtf8

class EditBookmarkDialog : ViewBindingDialogFragment<BookmarkEditBinding>("EditBookmarkDialog", BookmarkEditBinding::inflate) {
    private val bookmarkService: BookmarkService by instance()

    private val bookmarkTitle by fragmentArgument<String>("Bookmark")

    private val bookmark get() = bookmarkService.byTitle(bookmarkTitle)?.migrate()

    override fun onCreateDialog(contentView: View): Dialog {
        return bottomSheet(requireContext()) {
            title(R.string.bookmark_editor_title)
            contentView(contentView)
        }
    }

    private fun renameClicked() {
        val newTitle = views.bookmarkTitle.text.toString().take(64).trim()

        if (newTitle.length > 1 && newTitle != bookmarkTitle) {
            renameTo(newTitle)
        }

        dismiss()
    }

    private fun renameTo(newTitle: String) {
        this.bookmark?.let { bookmark ->
            bookmarkService.rename(bookmark, newTitle)
        }
    }

    private fun deleteClicked() {
        this.bookmark?.let { bookmark ->
            bookmarkService.delete(bookmark)
        }

        dismiss()
    }

    override fun onDialogViewCreated() {
        views.bookmarkTitle.setText(bookmarkTitle)

        views.actionSave.setOnClickListener { renameClicked() }

        views.actionDelete.setOnClickListener { deleteClicked() }

        if (ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())) {
            views.actionShortcut.isVisible = true
            views.actionShortcut.setOnClickListener { shortcutClicked() }
        }

        val feedStartWithThis = bookmark?.let { it.uri == Settings.feedStartWithUri } == true
        views.actionBookmarkDefault.isChecked = feedStartWithThis

        views.actionBookmarkDefault.setOnCheckedChangeListener { _, isChecked ->
            makeBookmarkTheDefaultFeed(isChecked)
        }
    }

    private fun makeBookmarkTheDefaultFeed(default: Boolean) {

        if (default) {
            Settings.feedStartWithUri = bookmark?.uri
        } else {
            Settings.feedStartWithUri = null
        }
    }

    private fun shortcutClicked() {
        val context = requireContext()
        val bookmark = this.bookmark ?: return

        // derive a unique id from bookmark link
        val id = "bookmark-${bookmark.link.encodeUtf8().md5()}"

        logger.debug { "Create shortcut for ${bookmark.uri} (id=$id)" }

        val intent = activityIntent<MainActivity>(context) {
            action = Intent.ACTION_VIEW
            data = bookmark.uri
        }

        val shortcutInfo = ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(bookmarkTitle)
                .setIntent(intent)
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_roundapp))
                .build()

        ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)

        dismiss()
    }

    companion object {
        fun forBookmark(b: Bookmark): EditBookmarkDialog {
            return EditBookmarkDialog().apply {
                arguments = bundleOf("Bookmark" to b.title)
            }
        }
    }
}
