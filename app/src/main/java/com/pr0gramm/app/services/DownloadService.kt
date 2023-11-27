package com.pr0gramm.app.services

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.core.net.toUri
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Logger
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.ui.base.launchIgnoreErrors
import com.pr0gramm.app.util.CountingInputStream
import com.pr0gramm.app.util.debugOnly
import com.pr0gramm.app.util.readStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.coroutineContext


/**
 */
class DownloadService(
    private val context: Application,
    private val notificationService: NotificationService,
    private val cache: Cache
) {

    /**
     * Enqueues an object for download. If an error occurs this method returns
     * the error string. You can then display it as you please.
     */
    suspend fun downloadWithNotification(feedItem: FeedItem, preview: Bitmap?) {
        withContext(Dispatchers.IO + NonCancellable) {
            // download over proxy to use caching
            val uri = UriHelper.NoPreload.mediaCompatible(feedItem, highQuality = true)

            val name = filenameOf(feedItem)

            val targetFile = Storage.create(context, name)
                ?: throw CouldNotCreateDownloadDirectoryException()

            try {
                downloadToFile(uri, targetFile).collect { status ->
                    notificationService.showDownloadNotification(targetFile, status.progress, preview)
                }

                triggerMediaScan(targetFile)

            } catch (err: Exception) {
                notificationService.showDownloadNotification(targetFile, -1f, preview)
                throw err
            }
        }
    }

    suspend fun downloadUpdateFile(uri: Uri): Flow<Status> {
        val target = runInterruptible(Dispatchers.IO) {
            val directory = File(context.cacheDir, "updates")
            logger.info { "Use download directory at $directory" }

            logger.info { "Create temporary directory" }
            directory.mkdirs()

            try {
                directory.listFiles()?.filter { it.isFile }?.forEach { file ->
                    logger.info { "Delete previously downloaded update file $file" }

                    if (!file.delete()) {
                        logger.warn { "Could not delete file $file" }
                    }
                }
            } catch (err: Exception) {
                logger.warn(err) { "Error during cache cleanup" }
            }

            // and download the new file.
            File.createTempFile("pr0gramm-update", ".apk", directory)
        }

        return downloadToFile(uri, target.toUri()).flowOn(Dispatchers.IO)
    }

    private fun downloadToFile(uri: Uri, target: Uri): Flow<Status> {
        return flow {

            emit(Status(progress = 0f))

            cache.get(uri).use { entry ->
                val totalSize = entry.totalSize

                Storage.openOutputStream(context, target).use { output ->
                    val publishState = OnceEvery(Duration.millis(250))

                    entry.inputStreamAt(0).use { body ->
                        CountingInputStream(body).use { input ->
                            readStream(input) { buffer, count ->
                                coroutineContext.ensureActive()

                                output.write(buffer, 0, count)

                                debugOnly {
                                    // simulate slow network connection in debug mode
                                    Thread.sleep(if (Math.random() < 0.1) 100 else 0)
                                }

                                // only publish status if we know the size of the file
                                if (totalSize > 0L) {
                                    publishState {
                                        emit(Status(progress = input.count / totalSize.toFloat()))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            emit(Status(1f, target))
        }
    }

    private fun triggerMediaScan(targetFile: Uri) {
        AsyncScope.launchIgnoreErrors {
            MediaScannerConnection.scanFile(context, arrayOf(targetFile.toString()), null) { path, uri ->
                logger.info { "Scanned $path ($uri)" }
            }
        }
    }

    class CouldNotCreateDownloadDirectoryException : IOException()

    class Status(val progress: Float, val file: Uri? = null) {
        val finished = file != null
    }

    companion object {
        private val logger = Logger("DownloadService")

        fun filenameOf(feedItem: FeedItem): String {
            val format = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault())
            val fileType = feedItem.path.takeLastWhile { it != '.' }.lowercase(Locale.ROOT)
            val prefix = listOf(
                feedItem.created.toString(format),
                feedItem.user,
                "id" + feedItem.id
            ).joinToString("-")

            return prefix.replace("[^A-Za-z0-9_-]+".toRegex(), "") + "." + fileType
        }
    }
}

