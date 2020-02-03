package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.model.bookmark.Bookmark
import com.pr0gramm.app.orm.link
import com.pr0gramm.app.orm.migrate
import com.pr0gramm.app.orm.uri
import com.pr0gramm.app.services.BookmarkService
import com.pr0gramm.app.ui.MainActivity
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.base.bindView
import com.pr0gramm.app.ui.bottomSheet
import com.pr0gramm.app.util.activityIntent
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.fragmentArgument
import okio.ByteString.Companion.encodeUtf8

class EditBookmarkDialog : BaseDialogFragment("EditBookmarkDialog") {
    private val bookmarkService: BookmarkService by instance()

    private val bookmarkTitle by fragmentArgument<String>("Bookmark")

    private val bookmarkTitleView: EditText by bindView(R.id.bookmark_name)

    private val buttonSave: Button by bindView(R.id.action_rename)
    private val buttonDelete: Button by bindView(R.id.action_delete)
    private val buttonShortcut: Button by bindView(R.id.action_shortcut)

    private val checkMakeDefault: Switch by bindView(R.id.action_bookmark_default)

    private val bookmark get() = bookmarkService.byTitle(bookmarkTitle)?.migrate()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return bottomSheet(requireContext()) {
            title(R.string.bookmark_editor_title)
            layout(R.layout.bookmark_edit)
        }
    }

    private fun renameClicked() {
        val newTitle = bookmarkTitleView.text.toString().take(64).trim()

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
        bookmarkTitleView.setText(bookmarkTitle)

        buttonSave.setOnClickListener { renameClicked() }

        buttonDelete.setOnClickListener { deleteClicked() }

        if (ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())) {
            buttonShortcut.isVisible = true
            buttonShortcut.setOnClickListener { shortcutClicked() }
        }

        val feedStartWithThis = bookmark?.let { it.uri == Settings.get().feedStartWithUri } == true
        checkMakeDefault.isChecked = feedStartWithThis

        checkMakeDefault.setOnCheckedChangeListener { _, isChecked ->
            makeBookmarkTheDefaultFeed(isChecked)
        }
    }

    private fun makeBookmarkTheDefaultFeed(default: Boolean) {
        val settings = Settings.get()

        if (default) {
            settings.feedStartWithUri = bookmark?.uri
        } else {
            settings.feedStartWithUri = null
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
