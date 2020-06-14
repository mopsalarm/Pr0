package com.pr0gramm.app.services

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toFile
import com.pr0gramm.app.Duration.Companion.millis
import com.pr0gramm.app.Logger
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.util.debugOnly
import com.pr0gramm.app.util.isLocalFile
import com.pr0gramm.app.util.readStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import pl.droidsonroids.gif.GifAnimationMetaData
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifDrawableBuilder
import pl.droidsonroids.gif.GifOptions
import java.io.File
import java.io.RandomAccessFile
import kotlin.coroutines.coroutineContext

/**
 */
class GifDrawableLoader(private val fileCache: File, private val cache: Cache) {
    private val logger = Logger("GifLoader")

    fun load(uri: Uri): Flow<State> {
        val result = flow {
            try {
                if (uri.isLocalFile) {
                    val drawable = RandomAccessFile(uri.toFile(), "r").use {
                        createGifDrawable(it)
                    }

                    emit(State(drawable))

                } else {
                    cache.get(uri).use { entry ->
                        emitAll(loadGifUsingTempFile(entry))
                    }
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
    private fun loadGifUsingTempFile(entry: Cache.Entry): Flow<State> {
        return flow {
            val temporary = File.createTempFile("tmp", ".gif", fileCache)

            logger.info { "storing data into temporary file" }

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
                        coroutineContext.ensureActive()

                        debugOnly {
                            // simulate slow network connection in debug mode
                            Thread.sleep(if (Math.random() < 0.1) 100 else 0)
                        }

                        storage.write(buffer, 0, length)
                        count += length

                        publishState {
                            emit(State(progress = count / contentLength))
                        }
                    }
                }

                emit(State(createGifDrawable(storage)))
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

    class State(
            val drawable: GifDrawable? = null,
            val progress: Float = 1.0f)
}
