package com.pr0gramm.app.services

import android.content.ContentResolver
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

    fun takePersistableUriPermission(context: Context, uri: Uri) {
        require(isTreeUri(uri)) { "Not a tree uri '$uri'" }

        logger.info { "takePersistableUriPermission($uri)" }

        val contentResolver = context.applicationContext.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        context.grantUriPermission(context.packageName, uri, flags)
        contentResolver.takePersistableUriPermission(uri, flags)
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

    fun isTreeUri(uri: Uri): Boolean {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == "com.android.externalstorage.documents") {
            val segments = uri.pathSegments
            return segments.size == 2 && segments[0] == "tree"
        }

        return false
    }

    fun defaultTree(context: Context): DocumentFile? {
        val uri = Settings.get().downloadTarget2 ?: return null
        return DocumentFile.fromTreeUri(context, uri)
    }

    fun create(context: Context, uri: Uri, file: String): Uri {
        val tree = DocumentFile.fromTreeUri(context, uri)
                ?: throw IOException("Error building DocumentFile from uri '$uri'")

        return create(tree, file)
    }

    fun create(context: Context, file: String): Uri? {
        val tree = defaultTree(context) ?: return null
        return create(tree, file)
    }

    fun create(tree: DocumentFile, file: String): Uri {
        val mime = MimeTypeHelper.guessFromFileExtension(file) ?: "application/octet-stream"
        val displayName = file.replaceAfterLast('.', "").trimEnd('.')

        return tree.createFile(mime, displayName)?.uri
                ?: throw IOException("Error creating file '$file' in tree")
    }

    fun toFile(uri: Uri): File {
        return File(File(uri.lastPathSegment ?: "/").name)
    }

    fun persistTreeUri(context: Context, resultIntent: Intent): Boolean {
        val uri = resultIntent.data ?: return false
        logger.warn { "Try to set '$uri' as new download directory" }

        if (!isTreeUri(uri)) {
            return false
        }

        takePersistableUriPermission(context, uri)

        return try {
            val testUri = create(context, uri, "pr0gramm-test.txt")

            logger.debug { "Test storage by writing to '$testUri'" }
            openOutputStream(context, testUri).use { out -> out.write("test".toByteArray()) }
            DocumentsContract.deleteDocument(context.contentResolver, testUri)

            logger.debug { "Storage looks good, saving new download directory." }
            Settings.get().edit { putString("pref_download_path", uri.toString()) }

            true

        } catch (err: Exception) {
            false
        }
    }
}
