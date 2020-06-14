package com.pr0gramm.app.services

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toFile
import com.pr0gramm.app.Duration.Companion.millis
import com.pr0gramm.app.Logger
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.util.createObservable
import com.pr0gramm.app.util.isLocalFile
import com.pr0gramm.app.util.readStream
import pl.droidsonroids.gif.GifAnimationMetaData
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifDrawableBuilder
import pl.droidsonroids.gif.GifOptions
import rx.Emitter
import rx.Observable
import rx.subscriptions.Subscriptions
import java.io.File
import java.io.RandomAccessFile
import java.lang.System.identityHashCode

/**
 */
class GifDrawableLoader(private val fileCache: File, private val cache: Cache) {
    private val logger = Logger("GifLoader")

    fun load(uri: Uri): Observable<Status> {
        return createObservable(Emitter.BackpressureMode.LATEST) { emitter ->
            try {
                if (uri.isLocalFile) {
                    val drawable = RandomAccessFile(uri.toFile(), "r").use { createGifDrawable(it) }

                    emitter.onNext(Status(drawable))
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
    private fun loadGifUsingTempFile(emitter: Emitter<in Status>,
                                     entry: Cache.Entry) {

        val temporary = File(fileCache, "tmp" + identityHashCode(emitter) + ".gif")

        logger.info { "storing data into temporary file" }

        val subscription = Subscriptions.empty()
        emitter.setSubscription(subscription)

        RandomAccessFile(temporary, "rw").use { storage ->
            // remove entry from filesystem now - the system will remove the data
            // when the stream closes.

            temporary.delete()

            val publishState = OnceEvery(millis(100))

            // copy data to the file.
            val contentLength = entry.totalSize.toFloat()

            entry.inputStreamAt(0).use { stream ->
                var count = 0
                readStream(stream) { buffer, length ->
                    storage.write(buffer, 0, length)
                    count += length

                    if (subscription.isUnsubscribed) {
                        logger.info { "Stopped because the subscriber unsubscribed" }
                        return
                    }

                    publishState {
                        emitter.onNext(Status(progress = count / contentLength))
                    }
                }
            }

            if (subscription.isUnsubscribed) {
                return
            }

            try {
                val drawable = createGifDrawable(storage)

                emitter.onNext(Status(drawable))
                emitter.onCompleted()
            } catch (error: Throwable) {
                emitter.onError(error)
            }
        }
    }

    private fun createGifDrawable(storage: RandomAccessFile): GifDrawable? {
        val meta = ParcelFileDescriptor.dup(storage.fd).use {
            GifAnimationMetaData(it.fileDescriptor)
        }

        val sampleSize = intArrayOf(1, 2, 3, 4, 5).firstOrNull { meta.width / it < 768 } ?: 6

        logger.info { "Loading gif ${meta.width}x${meta.height} with sampleSize $sampleSize" }

        return GifDrawableBuilder()
                .from(storage.fd)
                .options(GifOptions().apply {
                    setInSampleSize(sampleSize)
                    setInIsOpaque(true)
                })
                .setRenderingTriggeredOnDraw(true)
                .build()
    }

    data class Status(
            val drawable: GifDrawable? = null,
            val progress: Float = 1.0f) {

        val finished = drawable != null
    }
}
