package com.pr0gramm.app.services

import android.net.Uri
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.util.AndroidUtility.toFile
import com.pr0gramm.app.util.createObservable
import com.pr0gramm.app.util.logger
import com.pr0gramm.app.util.readStream
import pl.droidsonroids.gif.GifDrawable
import rx.Emitter
import rx.Observable
import rx.subscriptions.Subscriptions
import java.io.File
import java.io.RandomAccessFile
import java.lang.System.identityHashCode

/**
 */
class GifDrawableLoader(val fileCache: File, private val cache: Cache) {
    fun load(uri: Uri): Observable<DownloadStatus> {
        return createObservable(Emitter.BackpressureMode.LATEST) { emitter ->
            try {
                if (uri.scheme == "file") {
                    emitter.onNext(DownloadStatus(GifDrawable(toFile(uri))))
                    emitter.onCompleted()
                } else {
                    cache.get(uri).use { entry ->
                        loadGifUsingTempFile(emitter, entry)
                    }
                }

            } catch (error: Throwable) {
                logger.warn("Error during gif-loading", error)
                emitter.onError(error)
            }
        }
    }

    /**
     * Loads the data of the gif into a temporary file. The method then
     * loads the gif from this temporary file. The temporary file is removed
     * after loading the gif (or on failure).
     */
    private fun loadGifUsingTempFile(emitter: Emitter<in DownloadStatus>,
                                     entry: Cache.Entry) {

        val temporary = File(fileCache, "tmp" + identityHashCode(emitter) + ".gif")

        logger.info("storing data into temporary file")

        val subscription = Subscriptions.empty()
        emitter.setSubscription(subscription)

        RandomAccessFile(temporary, "rw").use { storage ->
            // remove entry from filesystem now - the system will remove the data
            // when the stream closes.

            temporary.delete()

            // copy data to the file.
            val iv = DownloadService.Interval(250)
            val contentLength = entry.totalSize.toFloat()
            entry.inputStreamAt(0).use { stream ->
                var count = 0
                readStream(stream) { buffer, length ->
                    storage.write(buffer, 0, length)
                    count += length

                    if (subscription.isUnsubscribed) {
                        logger.info("Stopped because the subscriber unsubscribed")
                        return
                    }

                    iv.doIfTime {
                        emitter.onNext(DownloadStatus(progress = count / contentLength))
                    }
                }
            }

            if (subscription.isUnsubscribed) {
                return
            }

            try {
                val drawable = GifDrawable(storage.fd)

                // closing is now delegated to the drawable.
                emitter.onNext(DownloadStatus(drawable))
                emitter.onCompleted()
            } catch (error: Throwable) {
                emitter.onError(error)
            }
        }
    }

    data class DownloadStatus(val drawable: GifDrawable? = null,
                              val progress: Float = 1.0f) {

        val finished get() = drawable != null
    }

    companion object {
        private val logger = logger("GifLoader")
    }
}
