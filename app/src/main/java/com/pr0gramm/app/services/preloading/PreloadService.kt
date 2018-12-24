package com.pr0gramm.app.services.preloading

import android.app.IntentService
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.services.DownloadService
import com.pr0gramm.app.services.NotificationService
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.AndroidUtility.toFile
import okhttp3.OkHttpClient
import okhttp3.Request
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.erased.instance
import java.io.*
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * This service handles preloading and resolving of preloaded images.
 */
class PreloadService : IntentService("PreloadService"), KodeinAware {
    override val kodein: Kodein by lazy { applicationContext.kodein }

    private val httpClient: OkHttpClient by instance()
    private val preloadManager: PreloadManager by instance()
    private val notificationService: NotificationService by instance()

    private val powerManager: PowerManager by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }

    @Volatile
    private var canceled: Boolean = false

    private var jobId: Long = 0
    private val interval = DownloadService.Interval(500)

    private lateinit var preloadCache: File

    private val notification by lazy {
        notificationService.beginPreloadNotification()
                .setContentTitle(getString(R.string.preload_ongoing))
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setTicker("")
    }

    override fun onCreate() {
        super.onCreate()

        // send out the initial notification and bring the service into foreground mode!
        startForeground(NotificationService.Types.Preload.id, notification.build())

        preloadCache = File(cacheDir, "preload").apply {
            if (mkdirs()) {
                logger.info { "preload directory created at ${this}" }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getLongExtra(EXTRA_CANCEL, -1) == jobId) {
            canceled = true
            return Service.START_NOT_STICKY
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onHandleIntent(intent: Intent?) {
        val items = parseFeedItemsFromIntent(intent)
        if (intent == null || items.isEmpty())
            return


        jobId = System.currentTimeMillis()
        canceled = false

        val cancelIntent = PendingIntent.getService(this, 0,
                Intent(this, PreloadService::class.java).putExtra(EXTRA_CANCEL, jobId),
                PendingIntent.FLAG_UPDATE_CURRENT)

        // update notification
        show {
            setProgress(100 * items.size, 0, false)
            addAction(R.drawable.ic_close_24dp, getString(R.string.cancel), cancelIntent)
            setContentIntent(cancelIntent)
        }

        val creation = Instant.now()
        val uriHelper = UriHelper.of(this)

        // create a wake lock
        val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, PreloadService::class.java.name)

        val allowOnlyOnMobile = !intent.getBooleanExtra(EXTRA_ALLOW_ON_MOBILE, false)

        try {
            logger.info { "Acquire wake lock for at most 10 minutes" }
            wakeLock.use(10, TimeUnit.MINUTES) {

                var statsFailed = 0
                var statsDownloaded = 0
                var idx = 0
                while (idx < items.size && !canceled) {
                    if (allowOnlyOnMobile && AndroidUtility.isOnMobile(this))
                        break

                    val item = items[idx]
                    try {
                        val mediaUri = uriHelper.media(item)
                        val mediaIsLocal = "file" == mediaUri.scheme
                        val mediaFile = if (mediaIsLocal) toFile(mediaUri) else cacheFileForUri(mediaUri)

                        val thumbUri = uriHelper.thumbnail(item)
                        val thumbIsLocal = "file" == thumbUri.scheme
                        val thumbFile = if (thumbIsLocal) toFile(thumbUri) else cacheFileForUri(thumbUri)

                        // update the notification
                        maybeShow {
                            setProgress(items.size, idx, false)
                        }

                        // prepare the entry that will be put into the database later
                        val entry = PreloadManager.PreloadItem(item.id, creation, mediaFile, thumbFile)

                        if (!mediaIsLocal)
                            download(2 * idx, 2 * items.size, mediaUri, entry.media)

                        if (!thumbIsLocal)
                            download(2 * idx + 1, 2 * items.size, thumbUri, entry.thumbnail)

                        preloadManager.store(entry)

                        statsDownloaded += 1
                    } catch (ioError: IOException) {
                        statsFailed += 1
                        logger.warn("Could not preload image id=" + item.id, ioError)
                    }

                    idx++
                }

                // doing cleanup
                doCleanup(createdBefore = Instant.now() - Duration.days(1))

                // setting end message
                showEndMessage(statsDownloaded, statsFailed)
            }

        } catch (error: Throwable) {
            if (error.rootCause !is IOException) {
                AndroidUtility.logToCrashlytics(error)
            }

            notification.setContentTitle(getString(R.string.preload_failed))

        } finally {
            logger.info { "Preloading finished" }

            // clear the action button
            notification.mActions.clear()

            show {
                setSmallIcon(android.R.drawable.stat_sys_download_done)
                setSubText(null)
                setProgress(0, 0, false)
                setOngoing(false)
                setContentIntent(null)
            }

            stopForeground(false)
        }
    }

    private fun parseFeedItemsFromIntent(intent: Intent?): List<FeedItem> {
        val feed = intent?.extras?.getParcelableArray(EXTRA_LIST_OF_ITEMS)
        return feed?.mapNotNull { it as? FeedItem } ?: listOf()
    }

    private fun showEndMessage(downloaded: Int, failed: Int) {
        val contentText = ArrayList<String>()
        contentText.add(getString(R.string.preload_sub_downloaded, downloaded))
        if (failed > 0) {
            contentText.add(getString(R.string.preload_sub_failed, failed))
        }

        if (canceled) {
            contentText.add(getString(R.string.preload_canceled))
        }

        show {
            setProgress(0, 0, true)
            setContentTitle(getString(R.string.preload_finished))
            setContentText(contentText.joinToString(", "))
        }
    }

    /**
     * Cleaning old files before the given threshold.
     */
    private fun doCleanup(createdBefore: Instant) {
        show {
            setContentText(getString(R.string.preload_cleanup))
            setProgress(1, 0, true)
        }

        preloadManager.deleteBefore(createdBefore)
    }

    private fun download(index: Int, total: Int, uri: Uri, targetFile: File) {

        // if the file exists, we dont need to download it again
        if (targetFile.exists()) {
            logger.info { "File $targetFile already exists" }

            if (!targetFile.setLastModified(System.currentTimeMillis()))
                logger.warn { "Could not touch file $targetFile" }

            return
        }

        val tempFile = File(targetFile.path + ".tmp")
        try {
            download(uri, tempFile) { progress ->
                val msg: String
                val progressCurrent = (100 * (index + progress)).toInt()
                val progressTotal = 100 * total

                if (canceled) {
                    msg = getString(R.string.preload_sub_finished)
                } else {
                    msg = getString(R.string.preload_fetching, uri.path)
                }

                maybeShow {
                    setContentText(msg).setProgress(progressTotal, progressCurrent, false)
                }
            }

            if (!tempFile.renameTo(targetFile))
                throw IOException("Could not rename file")

        } catch (error: Throwable) {
            if (!tempFile.delete())
                logger.warn { "Could not remove temporary file" }

            throw error
        }
    }

    private inline fun show(config: NotificationCompat.Builder.() -> Unit) {
        notification.config()
        notificationManager.notify(NotificationService.Types.Preload.id, notification.build())
    }

    private fun maybeShow(config: NotificationCompat.Builder.() -> Unit) {
        interval.doIfTime {
            show(config)
        }
    }

    private fun download(uri: Uri, targetFile: File, progress: (Float) -> Unit) {
        logger.info { "Start downloading $uri to $targetFile" }

        val request = Request.Builder().get().url(uri.toString()).build()
        val response = httpClient.newCall(request).execute()

        response.body()?.let { body ->
            val contentLength = body.contentLength()

            body.byteStream().use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    if (contentLength < 0) {
                        progress(0.0f)
                        inputStream.copyTo(outputStream)
                        progress(1.0f)
                    } else {
                        copyWithProgress(progress, contentLength, inputStream, outputStream)
                    }
                }
            }
        }
    }

    /**
     * Copies from the input stream to the output stream.
     * The progress is written to the given observable.
     */
    private fun copyWithProgress(
            progress: (Float) -> Unit, contentLength: Long,
            inputStream: InputStream, outputStream: OutputStream) {

        var totalCount: Long = 0
        readStream(inputStream) { buffer, count ->
            outputStream.write(buffer, 0, count)

            totalCount += count.toLong()
            progress(totalCount.toFloat() / contentLength)
        }
    }

    /**
     * Name of the cache file for the given [Uri].
     */
    private fun cacheFileForUri(uri: Uri): File {
        val filename = uri.toString().replaceFirst("https?://".toRegex(), "").replace("[^0-9a-zA-Z.]+".toRegex(), "_")
        return File(preloadCache, filename)
    }

    companion object {
        private val logger = Logger("PreloadService")
        private const val EXTRA_LIST_OF_ITEMS = "PreloadService.listOfItems"
        private const val EXTRA_CANCEL = "PreloadService.cancel"
        private const val EXTRA_ALLOW_ON_MOBILE = "PreloadService.allowOnMobile"

        fun preload(context: Context, items: List<FeedItem>, allowOnMobile: Boolean) {
            val intent = Intent(context, PreloadService::class.java)
            intent.putExtra(EXTRA_ALLOW_ON_MOBILE, allowOnMobile)
            intent.putExtra(EXTRA_LIST_OF_ITEMS, items.toTypedArray())
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
