package com.pr0gramm.app.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.pr0gramm.app.Logger
import com.pr0gramm.app.Settings
import com.pr0gramm.app.util.closeQuietly
import okio.IOException
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream

object Storage {
    private val logger = Logger("Storage")

    fun openTreeIntent(initial: Uri? = null): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            // Provide read access to files and sub-directories in the user-selected
            // directory.
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION

            if (initial != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Optionally, specify a URI for the directory that should be opened in
                // the system file picker when it loads.
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial)
            }
        }
    }

    fun openOutputStream(context: Context, uri: Uri): OutputStream {
        logger.info { "Try to open an OutputStream for '${uri}'" }

        val fp = context.applicationContext.contentResolver.openFileDescriptor(uri, "w")
                ?: throw IOException("openFileDescriptor returned 'null'.")

        val fout = FileOutputStream(fp.fileDescriptor)

        return object : FilterOutputStream(fout) {
            override fun write(b: ByteArray, off: Int, len: Int) {
                fout.write(b, off, len)
            }

            override fun close() {
                super.close()
                fp.closeQuietly()
            }
        }
    }

    fun hasTreeUri(context: Context): Boolean {
        return defaultTree(context)?.canWrite() ?: false
    }

    fun create(context: Context, file: String): Uri? {
        val tree = defaultTree(context) ?: return null
        return create(tree, file)
    }

    fun toFile(uri: Uri): File {
        return File(File(uri.lastPathSegment ?: "/").name)
    }

    fun persistTreeUri(context: Context, resultIntent: Intent): Boolean {
        val uri = resultIntent.data ?: return false
        logger.warn { "Try to set '$uri' as new download directory" }

        takePersistableUriPermission(context, uri)

        return try {
            val tree = DocumentFile.fromTreeUri(context, uri)
                    ?: throw IOException("Error building DocumentFile from uri '$uri'")

            logger.info { "Test if storage is writable at $uri" }
            if (!tree.isDirectory || !tree.canWrite()) {
                return false
            }

            // write a temporary file just to test if it really works!
            val testUri = create(tree, "pr0gramm-test.txt")

            logger.info { "Test storage by writing to '$testUri'" }
            openOutputStream(context, testUri).use { out -> out.write("test".toByteArray()) }
            DocumentsContract.deleteDocument(context.contentResolver, testUri)

            logger.info { "Storage looks good, saving new download directory." }
            Settings.get().edit { putString("pref_download_tree_uri", uri.toString()) }

            true

        } catch (err: Exception) {
            false
        }
    }

    private fun takePersistableUriPermission(context: Context, uri: Uri) {
        logger.info { "takePersistableUriPermission($uri)" }

        val contentResolver = context.applicationContext.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        context.grantUriPermission(context.packageName, uri, flags)
        contentResolver.takePersistableUriPermission(uri, flags)
    }

    private fun defaultTree(context: Context): DocumentFile? {
        val uri = Settings.get().downloadTreeUri ?: return null
        return DocumentFile.fromTreeUri(context, uri)
    }

    private fun create(tree: DocumentFile, file: String): Uri {
        val mime = MimeTypeHelper.guessFromFileExtension(file) ?: "application/octet-stream"
        val displayName = file.replaceAfterLast('.', "").trimEnd('.')

        return tree.createFile(mime, displayName)?.uri
                ?: throw IOException("Error creating file '$file' in tree")
    }
}
