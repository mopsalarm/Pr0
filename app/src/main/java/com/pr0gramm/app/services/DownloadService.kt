package com.pr0gramm.app.services

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Environment
import com.google.common.io.CountingInputStream
import com.google.common.io.PatternFilenameFilter
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.services.proxy.ProxyService
import com.pr0gramm.app.util.createObservable
import com.pr0gramm.app.util.readStream
import okhttp3.OkHttpClient
import okhttp3.Request
import org.joda.time.format.DateTimeFormat
import org.slf4j.LoggerFactory
import rx.Emitter
import rx.Observable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


/**
 */
class DownloadService(
        private val context: Application,
        private val notificationService: NotificationService,
        private val proxyService: ProxyService,
        private val okHttpClient: OkHttpClient) {

    private val settings: Settings = Settings.get()

    /**
     * Enqueues an object for download. If an error occurs this method returns
     * the error string. You can then display it as you please.
     */
    fun downloadWithNotification(feedItem: FeedItem, preview: Bitmap? = null): Observable<Status> = Observable.defer {
        Track.download()

        // download over proxy to use caching
        val url = proxyService.proxy(UriHelper.of(context).media(feedItem, true))

        val external = when (settings.downloadLocation) {
            context.getString(R.string.pref_downloadLocation_value_downloads) ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            context.getString(R.string.pref_downloadLocation_value_pictures) ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

            else ->
                Environment.getExternalStorageDirectory()
        }

        val targetDirectory = File(external, "pr0gramm")
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            throw CouldNotCreateDownloadDirectoryException()
        }

        val format = DateTimeFormat.forPattern("yyyyMMdd-HHmmss")
        val fileType = feedItem.image.toLowerCase().replaceFirst("^.*\\.(\\w+)$".toRegex(), "$1")
        val prefix = listOf(
                feedItem.created.toString(format),
                feedItem.user,
                "id" + feedItem.id()).joinToString("-")

        val targetFile = File(targetDirectory, prefix.replace("[^A-Za-z0-9_-]+".toRegex(), "") + "." + fileType)

        downloadToFile(url.toString(), targetFile)
                .startWith(Status(0f, null))
                .doOnNext { status ->
                    // update download notification
                    notificationService.showDownloadNotification(targetFile, status.progress, preview)
                }
                .doOnCompleted {
                    MediaScannerConnection.scanFile(context, arrayOf(targetFile.toString()), null) { path, uri ->
                        logger.info("Scanned {} ({})", path, uri)
                    }
                }
    }

    fun downloadUpdateFile(uri: String): Observable<Status> {
        val directory = File(context.cacheDir, "updates")
        if (!directory.exists() && !directory.mkdirs()) {
            logger.warn("Could not create download directory: {}", directory)
        }

        // clear all previous files
        directory.listFiles(PatternFilenameFilter("pr0gramm-update.*.apk")).forEach { file ->
            if (!file.delete()) {
                logger.warn("Could not delete file {}", file)
            }
        }

        // and download the new file.
        val tempFile = File.createTempFile("pr0gramm-update", ".apk", directory)

        return downloadToFile(uri, tempFile)
    }

    fun downloadToFile(uri: String, target: File): Observable<Status> {
        return createObservable(Emitter.BackpressureMode.LATEST) { emitter ->
            try {
                FileOutputStream(target).use { output ->
                    // now do the request
                    val request = Request.Builder().url(uri).build()
                    val call = okHttpClient.newCall(request)
                    emitter.setCancellation { call.cancel() }

                    val response = call.execute()
                    val interval = Interval(250)

                    response.body()?.let { body ->
                        CountingInputStream(body.byteStream()).use { input ->
                            readStream(input) { buffer, count ->
                                output.write(buffer, 0, count)

                                // only give status if we know the size of the file
                                if (body.contentLength() >= 0L) {
                                    interval.doIfTime {
                                        val progress = input.count / body.contentLength().toFloat()
                                        emitter.onNext(Status(progress, null))
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

    data class Status(val progress: Float, val file: File?) {
        val finished = file != null
    }

    companion object {
        private val logger = LoggerFactory.getLogger("DownloadService")
    }
}
