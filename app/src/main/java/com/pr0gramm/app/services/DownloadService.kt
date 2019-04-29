package com.pr0gramm.app.services

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import com.llamalab.safs.Files
import com.llamalab.safs.Path
import com.llamalab.safs.android.AndroidFiles
import com.pr0gramm.app.Logger
import com.pr0gramm.app.Settings
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.util.CountingInputStream
import com.pr0gramm.app.util.closeQuietly
import com.pr0gramm.app.util.createObservable
import com.pr0gramm.app.util.readStream
import rx.Emitter
import rx.Observable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


/**
 */
class DownloadService(
        private val context: Application,
        private val notificationService: NotificationService,
        private val cache: Cache) {

    private val settings: Settings = Settings.get()

    /**
     * Enqueues an object for download. If an error occurs this method returns
     * the error string. You can then display it as you please.
     */
    fun downloadWithNotification(feedItem: FeedItem, preview: Bitmap? = null): Observable<Status> = Observable.defer {
        // download over proxy to use caching
        val uri = UriHelper.of(context).media(feedItem, true)


        val target = settings.downloadTarget

        if (Files.notExists(target)) {
            try {
                Files.createDirectories(target)
            } catch (err: com.llamalab.safs.FileAlreadyExistsException) {
                // ignored
            } catch (err: IOException) {
                logger.warn(err) { "Could not create download directory" }
                throw CouldNotCreateDownloadDirectoryException()
            }
        }

        val name = filenameOf(feedItem)
        val targetFile = target.resolve(name)

        downloadToFile(uri, targetFile)
                .startWith(Status(0f, null))
                .doOnNext { status ->
                    // update download notification
                    notificationService.showDownloadNotification(targetFile, status.progress, preview)
                }
                .doOnCompleted {
                    MediaScannerConnection.scanFile(context, arrayOf(targetFile.toString()), null) { path, uri ->
                        logger.info { "Scanned $path ($uri)" }
                    }
                }
    }

    fun downloadUpdateFile(uri: Uri): Observable<Status> {
        val directory = AndroidFiles.getCacheDirectory().resolve("updates")
        logger.info { "Use download directory at $directory" }

        logger.info { "Create temporary directory" }
        Files.createDirectories(directory)

        try {
            Files.newDirectoryStream(directory).use { files ->
                for (file in files.filter { Files.isRegularFile(it) }) {
                    logger.info { "Delete previously downloaded update file $file" }

                    if (!Files.deleteIfExists(file)) {
                        logger.warn { "Could not delete file $file" }
                    }
                }
            }
        } catch (err: Exception) {
            logger.warn(err) { "Error during cache cleanup" }
        }

        // and download the new file.
        val target = Files.createTempFile(directory, "pr0gramm-update", ".apk")
        return downloadToFile(uri, target)
    }

    private fun downloadToFile(uri: Uri, target: Path): Observable<Status> {
        return createObservable(Emitter.BackpressureMode.LATEST) { emitter ->
            try {
                cache.get(uri).use { entry ->
                    val totalSize = entry.totalSize

                    Files.newOutputStream(target).use { output ->
                        val interval = Interval(250)

                        entry.inputStreamAt(0).use { body ->
                            emitter.setCancellation { body.closeQuietly() }

                            CountingInputStream(body).use { input ->
                                readStream(input) { buffer, count ->
                                    output.write(buffer, 0, count)

                                    // only give status if we know the size of the file
                                    if (totalSize > 0L) {
                                        interval.doIfTime {
                                            val progress = input.count / totalSize.toFloat()
                                            emitter.onNext(Status(progress, null))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                emitter.onNext(Status(1f, target))
                emitter.onCompleted()

            } catch (error: Throwable) {
                emitter.onError(error)
            }
        }
    }

    class Interval(val interval: Long) {
        var last = System.currentTimeMillis()

        inline fun doIfTime(fn: () -> Unit) {
            val now = System.currentTimeMillis()
            if (now - last > interval) {
                last = now
                fn()
            }
        }
    }

    class CouldNotCreateDownloadDirectoryException : IOException()

    data class Status(val progress: Float, val file: Path?) {
        val finished = file != null
    }

    companion object {
        private val logger = Logger("DownloadService")

        fun filenameOf(feedItem: FeedItem): String {
            val format = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault())
            val fileType = feedItem.image.toLowerCase().replaceFirst("^.*\\.(\\w+)$".toRegex(), "$1")
            val prefix = listOf(
                    feedItem.created.toString(format),
                    feedItem.user,
                    "id" + feedItem.id).joinToString("-")

            return prefix.replace("[^A-Za-z0-9_-]+".toRegex(), "") + "." + fileType
        }
    }
}
