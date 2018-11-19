package com.pr0gramm.app.ui.upload

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.BulletSpan
import android.text.style.LeadingMarginSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.MainThread
import androidx.core.text.bold
import androidx.core.text.inSpans
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxbinding.view.RxView
import com.jakewharton.rxbinding.widget.textChanges
import com.pr0gramm.app.R
import com.pr0gramm.app.RequestCodes
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.services.MimeTypeHelper
import com.pr0gramm.app.services.RulesService
import com.pr0gramm.app.services.UploadService
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.services.config.Config
import com.pr0gramm.app.ui.MainActivity
import com.pr0gramm.app.ui.TagSuggestionService
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.configureNewStyle
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment
import com.pr0gramm.app.ui.fragments.withBusyDialog
import com.pr0gramm.app.ui.showDialog
import com.pr0gramm.app.ui.views.BusyIndicator
import com.pr0gramm.app.ui.views.viewer.MediaUri
import com.pr0gramm.app.ui.views.viewer.MediaView
import com.pr0gramm.app.ui.views.viewer.MediaViews
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.AndroidUtility.checkMainThread
import com.trello.rxlifecycle.android.FragmentEvent
import org.kodein.di.erased.instance
import rx.Observable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * This activity performs the actual upload.
 */
class UploadFragment : BaseFragment("UploadFragment") {
    private val uploadService: UploadService by instance()
    private val rulesService: RulesService by instance()

    private val config: Config by instance()
    private val tagSuggestions: TagSuggestionService by instance()

    private val busyContainer: View by bindView(R.id.busy_container)
    private val busyIndicator: BusyIndicator by bindView(R.id.busy_indicator)
    private val busyState: TextView by bindView(R.id.busy_state)
    
    private val contentTypeGroup: RadioGroup by bindView(R.id.content_type_group)
    private val preview: FrameLayout by bindView(R.id.preview)
    private val scrollView: ScrollView by bindView(R.id.scroll_view)
    private val similarHintView: View by bindView(R.id.similar_hint)
    private val similarImages: SimilarImageView by bindView(R.id.similar_list)
    private val tags: MultiAutoCompleteTextView by bindView(R.id.tags)
    private val upload: Button by bindView(R.id.upload)
    private val tagOpinionHint: View by bindView(R.id.opinion_hint)

    private var file: File? = null
    private var fileMediaType: MediaUri.MediaType? = null

    private var uploadInfo: UploadService.State.Uploaded? = null

    private var urlArgument: Uri? by optionalFragmentArgument()
    private var mediaTypeArgument: String? by optionalFragmentArgument(default = "image/*")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_upload, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val uri = urlArgument
        if (uri != null) {
            handleImageUri(uri)

        } else if (savedInstanceState == null) {
            handleImagePickRequest()
        }

        // enable auto-complete
        tagSuggestions.setupView(tags)

        // add the small print to the view
        val smallPrintView = view.find<TextView>(R.id.small_print)
        rulesService.displayInto(smallPrintView)
        upload.setOnClickListener { onUploadClicked() }

        // react on change in the tag input window
        tags.textChanges().subscribe { text ->
            tagOpinionHint.visible = tagSuggestions.containsQuestionableTag(text)
        }
    }

    private fun handleImagePickRequest() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = mediaTypeArgument

        // check if someone can handle this intent
        if (!context.canStartIntent(intent)) {
            showDialog(this) {
                content(R.string.error_no_gallery_app)
                positive { activity?.finish() }
            }

            return
        }

        startActivityForResult(intent, RequestCodes.SELECT_MEDIA)
    }

    private fun onUploadClicked() {
        val file = file
        if (file == null) {
            showDialog(this) {
                content(R.string.hint_upload_something_happen_try_again)
                positive(R.string.okay) { activity?.finish() }
            }
            return
        }

        // we have an already started upload. lets just continue with it
        if (uploadInfo != null) {
            startUpload(file)
            return
        }

        uploadService.sizeOkay(file)
                .onErrorResumeNext(Observable.empty())
                .bindToLifecycleAsync()
                .subscribe { sizeOkay ->
                    if (sizeOkay) {
                        startUpload(file)
                    } else {
                        handleSizeNotOkay()
                    }
                }
    }

    private fun updateUploadState(state: UploadService.State) {
        val text = when (state) {
            is UploadService.State.Processing -> getString(R.string.upload_state_processing)
            is UploadService.State.Uploading -> getString(R.string.upload_state_uploading)
            is UploadService.State.Pending -> getString(R.string.upload_state_pending, state.position)
            is UploadService.State.Uploaded -> getString(R.string.upload_state_uploaded)
            else -> null
        }

        if (text == null) {
            busyState.visible = false
        } else {
            busyState.visible = true
            busyState.text = text
        }
    }

    private fun startUpload(file: File) {
        checkMainThread()
        setFormEnabled(false)

        // get those from UI
        val type = selectedContentType

        val tags = tags.text.split('#', ',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()

        val uploadInfo = uploadInfo

        busyContainer.visible = true

        // start the upload
        val upload = if (uploadInfo == null) {
            busyIndicator.progress = 0f
            uploadService.upload(file, type, tags)
        } else {
            busyIndicator.spin()
            uploadService.post(uploadInfo, type, tags, false)
        }

        logger.info { "Start upload of type $type with tags $tags" }
        upload.compose(bindUntilEventAsync(FragmentEvent.DESTROY_VIEW))
                .doAfterTerminate { busyContainer.visible = false }
                .subscribe({ state ->
                    updateUploadState(state)

                    when (state) {
                        is UploadService.State.Uploading -> {
                            if (state.progress < 0.99) {
                                logger.info { "Uploading, progress is ${state.progress}" }
                                busyIndicator.progress = state.progress
                            } else {
                                logger.info { "Uploading, progress is nearly finished" }
                                if (!busyIndicator.isSpinning)
                                    busyIndicator.spin()
                            }
                        }

                        is UploadService.State.Uploaded -> {
                            logger.info { "Got upload key, storing." }
                            this.uploadInfo = state
                        }

                        is UploadService.State.SimilarItems -> {
                            logger.info { "Found similar posts. Showing them now" }

                            showSimilarPosts(state.items)
                            setFormEnabled(true)
                        }

                        is UploadService.State.Error -> {
                            val err = UploadService.UploadFailedException(state.error, state.report)
                            onUploadError(err)
                        }

                        is UploadService.State.Success -> {
                            logger.info { "Finished! item id is ${state.id}" }
                            onUploadComplete(state.id)

                        }

                        else -> {
                            logger.info { "Upload state: $state" }
                            busyIndicator.spin()
                        }
                    }
                }, { this.onUploadError(it) })

        // scroll back up
        scrollView.fullScroll(View.FOCUS_UP)
    }

    private fun showSimilarPosts(similar: List<Api.Posted.SimilarItem>) {
        similarHintView.visible = true
        similarImages.visible = true
        similarImages.items = similar

        similarHintView.requestFocus()
    }

    private fun setFormEnabled(enabled: Boolean) {
        upload.isEnabled = enabled
        tags.isEnabled = enabled

        for (idx in 0..contentTypeGroup.childCount - 1) {
            val view = contentTypeGroup.getChildAt(idx)
            view.isEnabled = enabled
        }
    }

    private fun onUploadComplete(postId: Long) {
        logger.info { "Go to new post now: $postId" }
        val activity = activity ?: return

        val intent = Intent(activity, MainActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        intent.data = UriHelper.of(activity).post(FeedType.NEW, postId)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)

        activity.finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)

        logger.info { "Got response from image picker: rc=$resultCode, intent=$intent" }
        if (requestCode == RequestCodes.SELECT_MEDIA) {
            if (resultCode == Activity.RESULT_OK && intent != null) {
                val image = intent.data ?: run { activity?.finish(); return }
                handleImageUri(image)
            } else {
                activity?.finish()
            }
        }
    }

    @MainThread
    private fun handleImageUri(image: Uri) {
        checkMainThread()
        val activity = activity ?: return

        busyContainer.visible = true

        logger.info { "copy image to private memory" }
        copy(activity, image)
                .bindToLifecycleAsync()
                .subscribe({ this.onMediaFile(it) }, { this.onError(it) })
    }

    private fun onError(throwable: Throwable) {
        if (throwable.rootCause is MediaNotSupported) {
            showCanNotHandleTypeDialog()
            return
        }

        showDialog(this) {
            content(R.string.error_check_file_permission)
            positive(android.R.string.ok, { activity?.finish() })
            onCancel { activity?.finish() }
        }
    }

    private fun onUploadError(throwable: Throwable) {
        setFormEnabled(true)

        val activity = activity ?: return

        if (throwable is UploadService.UploadFailedException) {
            val causeText = getUploadFailureText(activity, throwable)
            showDialog(this) {
                content(causeText)
                positive()
            }

        } else {
            logger.error("Got an upload error", throwable)
            AndroidUtility.logToCrashlytics(throwable)

            val str = ErrorFormatting.getFormatter(throwable).getMessage(activity, throwable)
            ErrorDialogFragment.showErrorString(fragmentManager, str)
        }
    }

    private fun onMediaFile(file: File) {
        val activity = activity ?: return

        this.file = file

        logger.info { "loading file into view." }
        val uri = MediaUri.of(-1, Uri.fromFile(file))

        fileMediaType = uri.mediaType

        val viewer = MediaViews.newInstance(MediaView.Config(activity, uri))
        viewer.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)

        RxView.attaches(viewer).subscribe { viewer.playMedia() }

        preview.removeAllViews()
        preview.addView(viewer)

        busyContainer.visible = false
    }

    private fun handleSizeNotOkay() {
        checkMainThread()
        if (fileMediaType == MediaUri.MediaType.IMAGE) {
            showDialog(this) {
                content(R.string.hint_image_too_large)
                positive(R.string.down_size, { shrinkImage() })
                negative(android.R.string.no)
            }
        } else {
            showDialog(this) {
                content(R.string.upload_hint_too_large)
                positive()
            }
        }
    }

    private fun shrinkImage() {
        uploadService.downsize(file!!)
                .compose(bindToLifecycleAsync<File>())
                .withBusyDialog(this)
                .subscribeWithErrorHandling { newFile ->
                    handleImageUri(Uri.fromFile(newFile))
                    imageShrankSuccess()
                }
    }

    @MainThread
    private fun imageShrankSuccess() {
        view?.let { view ->
            Snackbar.make(view, R.string.hint_shrank_successful, Snackbar.LENGTH_LONG)
                    .configureNewStyle()
                    .setAction(R.string.okay, {})
                    .show()
        }
    }

    private fun showCanNotHandleTypeDialog() {
        showDialog(this) {
            content(R.string.upload_error_invalid_type)
            positive(R.string.okay, { activity?.finish() })
        }
    }

    private val selectedContentType: ContentType
        get() {
            val types = mapOf(
                    R.id.upload_type_sfw to ContentType.SFW,
                    R.id.upload_type_nsfw to ContentType.NSFW,
                    R.id.upload_type_nsfl to ContentType.NSFL)

            val view = view
            if (view != null) {
                for ((key, value) in types) {
                    val button = view.findOptional<RadioButton>(key)
                    if (button?.isChecked == true)
                        return value
                }
            }

            return ContentType.NSFL
        }

    @SuppressLint("NewApi")
    private fun copy(context: Context, source: Uri): Observable<File> {
        return Observable.fromCallable<File> {
            context.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input) { "Could not open input stream" }

                // read the "header"
                val bytes = ByteArray(512)
                val count = input.readSimple(bytes)

                // and guess the type
                val ext = MimeTypeHelper.guess(bytes)
                        ?.let { MimeTypeHelper.extension(it) }
                        ?: throw MediaNotSupported()

                // fail if we couldn't get an extension (and thereby a type)

                val target = makeTempUploadFile(context, ext)

                FileOutputStream(target).use { output ->
                    output.write(bytes, 0, count)

                    val maxSize = 1024 * 1024 * 48L
                    val copied = BoundedInputStream(input, maxSize).copyTo(output)
                    logger.info { "Copied ${ copied / 1024}kb" }

                    if (copied == maxSize) {
                        throw IOException("File too large.")
                    }
                }

                target
            }
        }
    }

    private fun makeTempUploadFile(context: Context, ext: String): File {
        return File(context.cacheDir, "upload." + ext)
    }

    private fun getUploadFailureText(context: Context, exception: UploadService.UploadFailedException): CharSequence {
        val reason = exception.errorCode
        val textId = when (reason) {
            "blacklisted" -> R.string.upload_error_blacklisted
            "internal" -> R.string.upload_error_internal
            "invalid" -> R.string.upload_error_invalid
            "download" -> R.string.upload_error_download_failed
            "exists" -> R.string.upload_error_exists
            else -> R.string.upload_error_unknown
        }

        return SpannableStringBuilder().apply {
            append(context.getString(textId))

            exception.report?.let { report ->
                val videoErrorId = when (report.error) {
                    "dimensionsTooSmall" -> R.string.upload_error_video_too_small
                    "dimensionsTooLarge" -> R.string.upload_error_video_too_large
                    "durationTooLong" -> R.string.upload_error_video_too_long
                    "invalidCodec" -> R.string.upload_error_video_codec
                    "invalidStreams" -> R.string.upload_error_video_streams
                    "invalidContainer" -> R.string.upload_error_video_container
                    else -> null
                }

                if (videoErrorId != null) {
                    append("\n\n")
                            .bold { append(context.getString(R.string.upload_error_video)) }
                            .append(" ")
                            .append(context.getString(videoErrorId))
                }

                append("\n\n")
                        .bold { append("Info:\n") }
                        .append(context.getString(R.string.report_video_summary,
                                report.width, report.height,
                                report.format, report.duration))
                        .append("\n")

                val offset = context.resources.getDimensionPixelSize(R.dimen.bullet_list_leading_margin)
                for (stream in report.streams) {
                    val streamInfo = context.getString(R.string.report_video_stream,
                            stream.type, stream.codec ?: "null")

                    inSpans(BulletSpan(offset / 3)) {
                        inSpans(LeadingMarginSpan.Standard(offset)) {
                            append(streamInfo)
                            append("\n")
                        }
                    }
                }
            }

            return@apply
        }
    }

    private class MediaNotSupported : RuntimeException("Media type not supported")

    companion object {
        fun forLocalUri(url: Uri?) = UploadFragment().apply { urlArgument = url }

        fun forMediaType(type: String?) = UploadFragment().apply { mediaTypeArgument = type }
    }
}
