package com.pr0gramm.app.services

import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.net.toFile
import com.pr0gramm.app.Duration.Companion.millis
import com.pr0gramm.app.Logger
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.util.debugOnly
import com.pr0gramm.app.util.isLocalFile
import com.pr0gramm.app.util.readStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import pl.droidsonroids.gif.GifAnimationMetaData
import pl.droidsonroids.gif.GifDrawableBuilder
import pl.droidsonroids.gif.GifOptions
import java.io.File
import java.io.RandomAccessFile

/**
 */
class GifDrawableLoader(private val cache: Cache) {
    private val logger = Logger("GifLoader")

    fun load(uri: Uri): Flow<State> {
        val result = flow {
            try {
                if (uri.isLocalFile) {
                    emit(State(createGifDrawable(uri.toFile())))
                } else {
                    emitAll(loadGifUsingCache(uri))
                }

            } catch (error: Throwable) {
                logger.warn(error) { "Error during gif-loading" }
                throw error
            }
        }

        return result.flowOn(Dispatchers.IO)
    }

    /**
     * Loads the data of the gif into a temporary file. The method then
     * loads the gif from this temporary file. The temporary file is removed
     * after loading the gif (or on failure).
     */
    private fun loadGifUsingCache(uri: Uri): Flow<State> {
        return flow {
            logger.info { "storing data into temporary file" }

            cache.get(uri).let { entry ->
                entry.file?.let { file ->
                    // we have a cached file, use that one.
                    return@flow emit(State(createGifDrawable(file)))
                }

                // read the gif once so it is in the cache.
                emitAll(readToCache(entry))
            }

            cache.get(uri).file?.let { file ->
                // we should now have a file there.
                return@flow emit(State(createGifDrawable(file)))
            }

            // crap, no file in cache, we cant do much now.
            logger.error { "No file in cache after reading the full file." }
        }
    }

    private fun readToCache(entry: Cache.Entry): Flow<State> {
        return flow {
            // read file once so we have it in the cache
            val publishState = OnceEvery(millis(100))
            val contentLength = entry.totalSize.toFloat()
            entry.inputStreamAt(0).use { stream ->
                var count = 0

                readStream(stream) { _, length ->
                    currentCoroutineContext().ensureActive()

                    debugOnly {
                        // simulate slow network connection in debug mode
                        Thread.sleep(if (Math.random() < 0.1) 100 else 0)
                    }

                    count += length

                    publishState {
                        emit(State(progress = count / contentLength))
                    }
                }
            }
        }
    }

    private fun createGifDrawable(file: File): Drawable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
        }

        // use the android-gif-drawable library to decode the image

        RandomAccessFile(file, "r").use { storage ->
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
    }

    class State(
        val drawable: Drawable? = null,
        val progress: Float = 1.0f
    )
}
