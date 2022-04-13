package com.pr0gramm.app.services

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.pr0gramm.app.Logger
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.seconds
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.ui.upload.UploadMediaType
import com.pr0gramm.app.ui.upload.UploadViewModel
import com.pr0gramm.app.ui.views.viewer.MediaUri
import com.pr0gramm.app.util.*
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runInterruptible
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import retrofit2.HttpException
import java.io.*
import java.util.*
import kotlin.time.ExperimentalTime


/**
 */
class UploadService(
    private val api: Api,
    private val userService: UserService,
    private val picasso: Picasso,
    private val configService: ConfigService,
    private val voteService: VoteService,
    private val applicationContext: Application
) {

    private val logger = Logger("UploadService")

    /**
     * Maximum upload size for the currently signed in user.
     */
    private fun maxUploadSize(type: MediaUri.MediaType): Long {
        val config = configService.config()

        return when (type) {
            MediaUri.MediaType.VIDEO -> config.maxUploadSizeVideo
            else -> config.maxUploadSizeImage
        }
    }

    private val maxUploadPixels: Long
        get() {
            val config = configService.config()
            return if (userService.loginState.premium) config.maxUploadPixelsPremium else config.maxUploadPixelsNormal
        }

    suspend fun sizeOkay(file: File, mediaType: UploadMediaType): Boolean {
        return runInterruptible(Dispatchers.IO) {
            val fileSizeOk = file.length() < maxUploadSize(mediaType.mediaUriType)
            if (!fileSizeOk) {
                return@runInterruptible false
            }

            if (mediaType === UploadMediaType.VIDEO) {
                return@runInterruptible true
            }

            try {
                // get image size, ignore errors
                val opts = BitmapFactory.Options()
                opts.inJustDecodeBounds = true
                BitmapFactory.decodeFile(file.path, opts)

                // check that image size is okay
                opts.outWidth * opts.outHeight <= maxUploadPixels

            } catch (err: Exception) {
                true
            }
        }
    }

    suspend fun shrinkImage(file: File): File {
        return runInterruptible(Dispatchers.IO) {
            val maxUploadSize = maxUploadSize(MediaUri.MediaType.IMAGE)

            logger.info {
                "Try to scale ${file.length() / 1024}kb image down to max of ${maxUploadSize / 1024}kb"
            }

            val bitmap = picasso.load(file)
                .config(Bitmap.Config.ARGB_8888)
                .centerInside()
                .resize(4096, 4096)
                .onlyScaleDown()
                .get()

            logger.info { "Image loaded at ${bitmap.width}x${bitmap.height}px" }

            // scale down to temp file
            val target = File.createTempFile("upload", "jpg", file.parentFile)
            target.deleteOnExit()

            val format = Bitmap.CompressFormat.JPEG
            var quality = 90
            do {
                FileOutputStream(target).use { output ->
                    logger.info { "Compressing to $format at quality=$quality" }
                    if (!bitmap.compress(format, quality, output))
                        throw IOException("Could not compress image data")

                    logger.info { "Size is now ${target.length() / 1024}kb" }
                }

                // decrease quality to shrink even further
                quality -= 10
            } while (target.length() >= maxUploadSize && quality > 30)

            logger.info { "Finished downsizing with an image size of ${target.length() / 1024}kb" }

            // simulate slow operation
            debugOnly { Thread.sleep(1_000) }

            target
        }
    }

    private fun upload(file: File): Flow<State> {
        val states = channelFlow {
            if (!file.exists() || !file.isFile || !file.canRead()) {
                throw FileNotFoundException("Can not read file to upload")
            }

            // prepare the upload
            this.trySend(State.Uploading(progress = 0f)).isSuccess

            val image = RequestBodyWithProgress(file) { state -> this.trySend(state).isSuccess }

            val body = MultipartBody.Builder()
                .setType("multipart/form-data".toMediaTypeOrNull()!!)
                .addFormDataPart("image", file.name, image)
                .build()

            // upload the actual file
            val response = api.upload(body)
            send(State.Uploaded(response.key))

            // track the uploaded file
            Track.upload(size = file.length())
        }

        return states.buffer(16).flowOn(Dispatchers.IO)
    }

    fun post(
        key: String, contentType: ContentType, userTags: Set<String>,
        checkSimilar: Boolean
    ): Flow<State> {

        val contentTypeTag = contentType.name.lowercase(Locale.ROOT)
        val tags = userTags.map { it.trim() }.filter { isValidTag(it) }

        // we can only post 4 extra tags with an upload, so lets add the rest later
        val firstTags: List<String>
        val extraTags: List<String>

        if (contentType == ContentType.SFW) {
            firstTags = tags.take(5)
            extraTags = tags.drop(5)
        } else {
            // we need to add the nsfw/nsfl tag to the list of tags.
            // It looks like the sfw status is getting ignored
            firstTags = listOf(contentTypeTag) + tags.take(4)
            extraTags = tags.drop(4)
        }

        val uploadState = flow {
            val posted = api.post(null, contentTypeTag, firstTags.joinToString(","), checkSimilar.toInt(), key, 1)

            val state = postedToState(key, posted)

            if (state is State.Pending) {
                emitAll(waitOnQueue(state.queue))
            } else {
                emit(state)
            }
        }

        return uploadState.onEach { state ->
            if (state is State.Success && extraTags.isNotEmpty()) {
                ignoreAllExceptions {
                    voteService.createTags(state.id, extraTags)
                }
            }
        }
    }

    private fun postedToState(key: String, posted: Api.Posted): State {
        if (posted.similar.isNotEmpty()) {
            return State.SimilarItems(key, posted.similar)
        }

        val error = posted.error
        if (error != null) {
            return State.Error(error, posted.report)
        }

        if (posted.itemId > 0) {
            return State.Success(posted.itemId)
        }

        posted.queueId?.let { queue ->
            return State.Pending(queue, -1)
        }

        throw IllegalStateException("can not map result to state")
    }

    @OptIn(ExperimentalTime::class)
    private fun waitOnQueue(queue: Long): Flow<State> {
        return flow {
            while (true) {
                kotlinx.coroutines.delay(2.seconds.inMillis)

                // get the current state from the queue - on error just try again.
                val info = try {
                    api.queue(queue)
                } catch (err: Exception) {
                    logger.warn(err) { "Error polling queue" }
                    continue
                }

                val itemId = info.item?.id ?: 0
                when {
                    info.status == "done" && itemId > 0 ->
                        return@flow emit(State.Success(itemId))

                    info.status == "processing" ->
                        emit(State.Processing)

                    else ->
                        emit(State.Pending(queue, info.position))
                }
            }
        }
    }

    sealed class State {
        class Error(val error: String, val report: Api.Posted.VideoReport?) : State()
        class Uploading(val progress: Float) : State()
        class Uploaded(val key: String) : State()
        class SimilarItems(val key: String, val items: List<Api.Posted.SimilarItem>) : State()
        class Success(val id: Long) : State()
        class Pending(val queue: Long, val position: Long) : State()
        object Processing : State()
    }

    fun upload(file: File, sfw: ContentType, tags: Set<String>): Flow<State> {
        return upload(file).transform { state ->
            emit(state)

            if (state is State.Uploaded) {
                emitAll(post(state.key, sfw, tags, true))
            }
        }
    }

    /**
     * Checks if the current user is rate limited. Returns true, if the user
     * is not allowed to upload an image right now. Returns false, if the user is
     * allowed to upload an image.
     */
    suspend fun checkIsRateLimited(): Boolean {
        try {
            api.ratelimited()
            return false
        } catch (err: Throwable) {
            if (err is HttpException && err.code() == 403) {
                return true
            }

            throw err
        }
    }

    suspend fun copyToStaging(source: Uri): Pair<File, UploadMediaType> {
        fun makeTempUploadFile(ext: String): File {
            return File(applicationContext.cacheDir, "upload.${System.currentTimeMillis()}.$ext").also { file ->
                // cleanup if the application exits
                file.deleteOnExit()
            }
        }

        return runInterruptible(Dispatchers.IO) {
            applicationContext.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input) { "Could not open input stream" }

                // read the first few bytes as "header"
                val bytes = BoundedInputStream(input, 512).readBytes()

                // and guess the type, also fail if we couldn't get
                // an extension (and thereby a type)
                val mimeType = MimeTypeHelper.guess(bytes)
                val ext = mimeType
                    ?.let { MimeTypeHelper.extension(it) }
                    ?: throw UploadViewModel.MediaNotSupportedException()

                logger.info { "Creating temporary file with extension '$ext'" }
                val target = makeTempUploadFile(ext)

                FileOutputStream(target).use { output ->
                    output.write(bytes)

                    val maxSize = configService.config().maxUploadSizeVideo + 1
                    val copied = BoundedInputStream(input, maxSize).copyTo(output)
                    logger.info { "Copied ${copied / 1024}kb (max is ${maxSize / 1024}kb" }

                    if (copied == maxSize) {
                        throw IOException("File too large.")
                    }
                }

                val uploadMediaType = when {
                    mimeType.startsWith("video/") -> UploadMediaType.VIDEO
                    else -> UploadMediaType.IMAGE
                }

                Pair(target, uploadMediaType)
            }
        }
    }

    class UploadFailedException(message: String, val report: Api.Posted.VideoReport?) : Exception(message) {
        val errorCode: String get() = message!!
    }

    private class RequestBodyWithProgress(private val file: File, private val publishState: (State) -> Unit) :
        RequestBody() {
        override fun contentType(): MediaType {
            val guessed = runCatching { MimeTypeHelper.guess(file)?.toMediaTypeOrNull() }
            return guessed.getOrNull() ?: "image/jpeg".toMediaType()
        }

        override fun contentLength(): Long {
            return file.length()
        }

        override fun writeTo(sink: BufferedSink) {
            val length = file.length().toFloat()

            var lastTime = 0L
            FileInputStream(file).use { input ->
                // send first progress report.
                publishState(State.Uploading(progress = 0f))

                var sent = 0
                readStream(input) { buffer, len ->
                    sink.write(buffer, 0, len)
                    sent += len

                    val now = System.currentTimeMillis()
                    if (now - lastTime >= 100) {
                        lastTime = now

                        // send progress now.
                        val progress = sent / length
                        publishState(State.Uploading(progress = progress))
                    }
                }

                // tell that the file is sent
                publishState(State.Uploading(progress = 1f))
            }
        }
    }
}

fun isValidTag(tag: String): Boolean {
    val invalidTags = setOf("sfw", "nsfw", "nsfl", "nsfp", "gif", "video", "sound", "text")
    val invalid = tag.lowercase(Locale.ROOT) in invalidTags || tag.length < 2 || tag.length > 32
    return !invalid
}

fun isMoreRestrictiveContentTypeTag(tags: List<String>, tag: String): Boolean {
    val sorted = listOf("sfw", "nsfp", "nsfw", "nsfl")

    val newTagIndex = sorted.indexOf(tag.lowercase(Locale.ROOT))
    if (newTagIndex < 0) {
        return false
    }

    val maxExistingTagIndex = tags.map { sorted.indexOf(it.lowercase(Locale.ROOT)) }.maxOrNull()
    return maxExistingTagIndex == null || maxExistingTagIndex < newTagIndex
}

