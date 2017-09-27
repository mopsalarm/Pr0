package com.pr0gramm.app.services

import android.graphics.Bitmap
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.ui.dialogs.ignoreError
import com.pr0gramm.app.util.readStream
import com.pr0gramm.app.util.subscribeOnBackground
import com.pr0gramm.app.util.toInt
import com.squareup.picasso.Picasso
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import org.slf4j.LoggerFactory
import retrofit2.HttpException
import rx.Observable
import rx.lang.kotlin.ofType
import rx.subjects.BehaviorSubject
import java.io.*


/**
 */
class UploadService(private val api: Api,
                    private val userService: UserService,
                    private val picasso: Picasso,
                    private val configService: ConfigService,
                    private val voteService: VoteService,
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
                return try {
                    MimeTypeHelper.guess(file)?.let { MediaType.parse(it) } ?: fallback
                } catch (ignored: IOException) {
                    fallback
                }
            }

            override fun contentLength(): Long {
                return file.length()
            }

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
                .map { response -> UploadInfo(response.key, emptyList()) }
                .subscribeOnBackground()
                .subscribe(result)

        return result.ignoreElements().mergeWith(result)
    }

    private fun post(key: String, contentType: ContentType, tags_: Set<String>,
                     checkSimilar: Boolean): Observable<Api.Posted> {

        val contentTypeTag = contentType.name.toLowerCase()
        val tags = tags_.map { it.trim() }.filter { isValidTag(it) }

        // we can only post 4 extra tags with an upload, so lets add the rest later
        val firstTags: List<String>
        val extraTags: List<String>

        if (contentType == ContentType.SFW) {
            firstTags = tags.take(5)
            extraTags = tags.drop(5)
        } else {
            // we need to add the nsfw/nsfl tag to the list of tags.
            // It looks like the sfwstatus is getting ignored
            firstTags = listOf(contentTypeTag) + tags.take(4)
            extraTags = tags.drop(4)
        }

        return api
                .post(null, contentTypeTag, firstTags.joinToString(","), checkSimilar.toInt(), key)
                .doOnNext { posted ->
                    // cache tags so that they appear immediately
                    if (posted.itemId > 0) {
                        cacheService.cacheTags(posted.itemId, tags)
                    }
                }
                .flatMap { posted ->
                    if (extraTags.isNotEmpty() && posted.itemId > 0) {
                        // try to add the extra parameters.
                        voteService.tag(posted.itemId, extraTags)
                                .ignoreError()
                                .ofType<Api.Posted>()
                                .concatWith(Observable.just(posted))

                    } else {
                        Observable.just(posted)
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

            if (response.similar.isNotEmpty()) {
                Observable.just(UploadInfo(status.key, response.similar))

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

    class UploadInfo(val key: String? = null,
                     val similar: List<Api.Posted.SimilarItem> = emptyList(),
                     val id: Long = 0,
                     val progress: Float = -1F) {

        val finished = id > 0
        val hasSimilar = similar.isNotEmpty()
    }

    class UploadFailedException(message: String, val report: Api.Posted.VideoReport?) : Exception(message) {
        val errorCode: String get() = message!!
    }

    companion object {
        private val logger = LoggerFactory.getLogger("UploadService")

    }
}

fun isValidTag(tag: String): Boolean {
    val validTags = setOf("sfw", "nsfw", "nsfl", "nsfp", "gif", "webm", "sound")
    val isInvalid = tag in validTags || tag.length < 2 || tag.length > 32
    return !isInvalid
}
