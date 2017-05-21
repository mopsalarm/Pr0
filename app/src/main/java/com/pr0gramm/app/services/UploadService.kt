package com.pr0gramm.app.services

import android.annotation.SuppressLint
import android.graphics.Bitmap
import com.google.common.base.Optional
import com.pr0gramm.app.HasThumbnail
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.util.readStream
import com.pr0gramm.app.util.subscribeOnBackground
import com.squareup.picasso.Picasso
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import org.slf4j.LoggerFactory
import retrofit2.HttpException
import rx.Observable
import rx.subjects.BehaviorSubject
import java.io.*


/**
 */
class UploadService(private val api: Api,
                    private val userService: UserService,
                    private val picasso: Picasso,
                    private val configService: ConfigService,
                    private val cacheService: InMemoryCacheService) {

    private val maxSize: Observable<Long> get() {
        return userService.loginState().take(1).map { state ->
            if (state.premium)
                configService.config().getMaxUploadSizePremium()
            else
                configService.config().getMaxUploadSizeNormal()
        }
    }

    fun sizeOkay(file: File): Observable<Boolean> {
        return maxSize.map { file.length() < it }
    }

    fun downsize(file: File): Observable<File> {
        return maxSize.flatMap { maxSize ->
            Observable.fromCallable {
                logger.info("Try to scale {}kb image down to max of {}kb",
                        file.length() / 1024, maxSize / 1024)

                val bitmap = picasso.load(file)
                        .config(Bitmap.Config.ARGB_8888)
                        .centerInside()
                        .resize(2048, 2048)
                        .onlyScaleDown()
                        .get()

                logger.info("Image loaded at {}x{}px", bitmap.width, bitmap.height)

                // scale down to temp file
                val target = File.createTempFile("upload", "jpg", file.parentFile)
                target.deleteOnExit()

                val format = Bitmap.CompressFormat.JPEG
                var quality = 90
                do {
                    FileOutputStream(target).use { output ->
                        logger.info("Compressing to {} at quality={}", format, quality)
                        if (!bitmap.compress(format, quality, output))
                            throw IOException("Could not compress image data")

                        logger.info("Size is now {}kb", target.length() / 1024)
                    }

                    // decrease quality to shrink even further
                    quality -= 10
                } while (target.length() >= maxSize && quality > 30)

                logger.info("Finished downsizing with an image size of {}kb", target.length() / 1024)
                target
            }
        }
    }

    private fun upload(file: File): Observable<UploadInfo> {
        if (!file.exists() || !file.isFile || !file.canRead())
            return Observable.error<UploadInfo>(FileNotFoundException("Can not read file to upload"))

        val result = BehaviorSubject.create(UploadInfo(progress = 0f)).toSerialized()

        val output = object : RequestBody() {
            override fun contentType(): MediaType {
                val fallback = MediaType.parse("image/jpeg")!!
                try {
                    return MimeTypeHelper.guess(file)?.let { MediaType.parse(it) } ?: fallback
                } catch (ignored: IOException) {
                    return fallback
                }
            }

            @Throws(IOException::class)
            override fun contentLength(): Long {
                return file.length()
            }

            @SuppressLint("NewApi")
            @Throws(IOException::class)
            override fun writeTo(sink: BufferedSink) {
                val length = file.length().toFloat()

                var lastTime = 0L
                FileInputStream(file).use { input ->
                    // send first progress report.
                    result.onNext(UploadInfo(progress = 0f))

                    var sent = 0
                    readStream(input) { buffer, len ->
                        sink.write(buffer, 0, len)
                        sent += len

                        val now = System.currentTimeMillis()
                        if (now - lastTime >= 100) {
                            lastTime = now

                            // send progress now.
                            val progress = sent / length
                            result.onNext(UploadInfo(progress = progress))
                        }
                    }

                    // tell that the file is sent
                    result.onNext(UploadInfo(progress = 1f))
                }
            }
        }

        val body = MultipartBody.Builder()
                .setType(MediaType.parse("multipart/form-data"))
                .addFormDataPart("image", file.name, output)
                .build()

        val size = file.length()

        // perform the upload!
        api.upload(body)
                .doOnEach { Track.upload(size) }
                .map { response -> UploadInfo(key = response.key, similar = emptyList()) }
                .subscribeOnBackground()
                .subscribe(result)

        return result.ignoreElements().mergeWith(result)
    }

    private fun post(key: String, contentType: ContentType, tags_: Set<String>,
                     checkSimilar: Boolean): Observable<Api.Posted> {

        val contentTypeTag = contentType.name.toLowerCase()

        val tags = tags_.map { it.trim() }
                .filter { isValidTag(it) }
                .plus(contentTypeTag)

        val tagStr = tags.joinToString(",")

        return api
                .post(null, contentTypeTag, tagStr, if (checkSimilar) 1 else 0, key)
                .doOnNext { posted ->
                    // cache tags so that they appear immediately
                    if (posted.itemId > 0) {
                        cacheService.cacheTags(posted.itemId, tags)
                    }
                }
    }

    fun upload(file: File, sfw: ContentType, tags: Set<String>): Observable<UploadInfo> {
        return upload(file).flatMap<UploadInfo> { status ->
            if (status.key != null) {
                post(status, sfw, tags, true)
            } else {
                Observable.just(status)
            }
        }
    }

    fun post(status: UploadInfo, contentType: ContentType, tags: Set<String>,
             checkSimilar: Boolean): Observable<UploadInfo> {

        return post(status.key!!, contentType, tags, checkSimilar).flatMap { response ->
            val error = response.error
            val similar = response.similar.size

            if (similar > 0) {
                Observable.just(UploadInfo(key = status.key, similar = response.similar))

            } else if (error != null) {
                Observable.error(UploadFailedException(error, response.report))

            } else {
                Observable.just(UploadInfo(id = response.getItemId()))
            }
        }
    }

    /**
     * Checks if the current user is rate limited. Returns true, if the user
     * is not allowed to upload an image right now. Returns false, if the user is
     * allowed to upload an image.
     */
    fun checkIsRateLimited(): Observable<Boolean> {
        return api.ratelimited().map { false }.onErrorResumeNext { error ->
            if (error is HttpException && error.code() == 403) {
                Observable.just(true)
            } else {
                Observable.error(error)
            }
        }
    }

    private fun isValidTag(tag: String): Boolean {
        return !(tag in INVALID_TAGS || tag.length < 2 || tag.length > 32)
    }

    class UploadInfo(val key: String? = null,
                     val similar: List<HasThumbnail> = emptyList(),
                     val id: Long = 0,
                     val progress: Float = -1F) {

        val finished = id > 0
        val hasSimilar = similar.isNotEmpty()
    }

    class UploadFailedException(message: String, val report: Optional<Api.Posted.VideoReport>) : Exception(message) {
        val errorCode: String get() = message!!
    }

    companion object {
        private val logger = LoggerFactory.getLogger("UploadService")

        private val INVALID_TAGS = setOf(
                "sfw", "nsfw", "nsfl", "nsfp", "gif", "webm", "sound")
    }
}
