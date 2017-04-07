package com.pr0gramm.app.services

import android.annotation.TargetApi
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.google.common.base.Joiner
import com.google.common.base.Optional
import com.google.common.io.ByteStreams
import com.google.common.io.CountingInputStream
import com.google.common.io.PatternFilenameFilter
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.services.proxy.ProxyService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.joda.time.format.DateTimeFormat
import org.slf4j.LoggerFactory
import rx.Emitter
import rx.Observable
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 */
@Singleton
class DownloadService @Inject
constructor(private val downloadManager: DownloadManager,
            private val proxyService: ProxyService,
            private val context: Context,
            private val okHttpClient: OkHttpClient) {

    private val settings: Settings = Settings.of(context)

    /**
     * Enqueues an object for download. If an error occurs this method returns
     * the error string. You can then display it as you please.
     */
    fun download(feedItem: FeedItem): Optional<String> {
        // download over proxy to use caching
        val url = proxyService.proxy(UriHelper.of(context).media(feedItem, true))

        val external = when (settings.downloadLocation(context)) {
            context.getString(R.string.pref_downloadLocation_value_downloads) ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            context.getString(R.string.pref_downloadLocation_value_pictures) ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

            else ->
                Environment.getExternalStorageDirectory()
        }

        val targetDirectory = File(external, "pr0gramm")
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            return Optional.of(context.getString(R.string.error_could_not_create_download_directory))
        }

        val format = DateTimeFormat.forPattern("yyyyMMdd-HHmmss")
        val fileType = feedItem.image().toLowerCase().replaceFirst("^.*\\.(\\w+)$".toRegex(), "$1")
        val prefix = Joiner.on("-").join(
                feedItem.created().toString(format),
                feedItem.user(),
                "id" + feedItem.id())

        val name = prefix.replace("[^A-Za-z0-9_-]+".toRegex(), "") + "." + fileType

        val request = DownloadManager.Request(url)
        request.setVisibleInDownloadsUi(false)
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setTitle(name)
        request.setDestinationUri(Uri.fromFile(File(targetDirectory, name)))

        request.allowScanningByMediaScanner()

        downloadManager.enqueue(request)

        Track.download()
        return Optional.absent<String>()
    }


    @TargetApi(Build.VERSION_CODES.KITKAT)
    fun downloadToFile(uri: String): Observable<Status> {
        return Observable.create<Status>({ subscriber ->
            try {
                val directory = File(context.cacheDir, "updates")
                if (!directory.exists() && !directory.mkdirs()) {
                    logger.warn("Could not create apk directory: {}", directory)
                }

                // clear all previous files
                directory.listFiles(PatternFilenameFilter("pr0gramm-update.*apk")).forEach { file ->
                    if (!file.delete()) {
                        logger.warn("Could not delete file {}", file)
                    }
                }

                // and download the new file.
                val tempFile = File.createTempFile("pr0gramm-update", ".apk", directory)

                FileOutputStream(tempFile).use { output ->
                    // now do the request
                    val request = Request.Builder().url(uri).build()
                    val call = okHttpClient.newCall(request)
                    subscriber.setCancellation { call.cancel() }

                    val response = call.execute()
                    val interval = Interval(250)
                    CountingInputStream(response.body().byteStream()).use { input ->
                        val buffer = ByteArray(1024 * 32)

                        while (true) {
                            val count = ByteStreams.read(input, buffer, 0, buffer.size)
                            if (count <= 0) {
                                break
                            }

                            output.write(buffer, 0, count)

                            interval.doIfTime {
                                val progress = input.count / response.body().contentLength().toFloat()
                                subscriber.onNext(Status(progress, null))
                            }
                        }
                    }
                }

                subscriber.onNext(Status(1f, tempFile))
                subscriber.onCompleted()

            } catch (error: Throwable) {
                subscriber.onError(error)
            }
        }, Emitter.BackpressureMode.LATEST)
    }

    private class Interval(private val interval: Long) {
        private var last = System.currentTimeMillis()

        inline fun doIfTime(fn: () -> Unit) {
            val now = System.currentTimeMillis()
            if (now - last > interval) {
                last = now
                fn()
            }
        }
    }

    data class Status(val progress: Float, val file: File?) {
        val finished = file != null
    }

    companion object {
        private val logger = LoggerFactory.getLogger("DownloadService")
    }
}
