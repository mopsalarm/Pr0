package com.pr0gramm.app.ui.upload

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.annotation.MainThread
import android.support.design.widget.Snackbar
import android.text.style.BulletSpan
import android.text.style.LeadingMarginSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import butterknife.ButterKnife
import com.github.salomonbrys.kodein.instance
import com.google.common.base.MoreObjects.firstNonNull
import com.google.common.base.Optional
import com.google.common.base.Preconditions.checkNotNull
import com.google.common.base.Splitter
import com.google.common.base.Throwables
import com.google.common.collect.ImmutableMap
import com.google.common.io.ByteStreams
import com.jakewharton.rxbinding.view.RxView
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.HasThumbnail
import com.pr0gramm.app.R
import com.pr0gramm.app.RequestCodes
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.services.MimeTypeHelper
import com.pr0gramm.app.services.RulesService
import com.pr0gramm.app.services.UploadService
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.ui.MainActivity
import com.pr0gramm.app.ui.TagInputView
import com.pr0gramm.app.ui.Truss
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.defaultOnError
import com.pr0gramm.app.ui.fragments.withBusyDialog
import com.pr0gramm.app.ui.showDialog
import com.pr0gramm.app.ui.views.BusyIndicator
import com.pr0gramm.app.ui.views.viewer.MediaUri
import com.pr0gramm.app.ui.views.viewer.MediaView
import com.pr0gramm.app.ui.views.viewer.MediaViews
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.AndroidUtility.checkMainThread
import com.pr0gramm.app.util.ErrorFormatting
import com.trello.rxlifecycle.android.FragmentEvent
import kotterknife.bindView
import org.slf4j.LoggerFactory
import rx.Observable
import rx.functions.Action1
import java.io.File
import java.io.FileOutputStream

/**
 * This activity performs the actual upload.
 */
class UploadFragment : BaseFragment() {
    private val uploadService: UploadService by instance()
    private val rulesService: RulesService by instance()

    private val busyIndicator by bindView<BusyIndicator>(R.id.busy_indicator)
    private val contentTypeGroup by bindView<RadioGroup>(R.id.content_type_group)
    private val preview by bindView<FrameLayout>(R.id.preview)
    private val scrollView by bindView<ScrollView>(R.id.scrollView)
    private val similarHintView by bindView<View>(R.id.similar_hint)
    private val similarImages by bindView<SimilarImageView>(R.id.similar_list)
    private val tags by bindView<MultiAutoCompleteTextView>(R.id.tags)
    private val upload by bindView<Button>(R.id.upload)

    private var file: File? = null
    private var fileMediaType: MediaUri.MediaType? = null
    private var uploadInfo: UploadService.UploadInfo? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_upload, container, false)
    }

    override fun injectComponent(activityComponent: ActivityComponent) {
        activityComponent.inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val uri = urlArgument
        if (uri.isPresent) {
            handleImageUri(uri.get())

        } else if (savedInstanceState == null) {
            val type = arguments?.getString(EXTRA_MEDIA_TYPE) ?: "image/*"

            val intent = Intent(Intent.ACTION_PICK)
            intent.type = type
            startActivityForResult(intent, RequestCodes.SELECT_MEDIA)
        }

        // enable auto-complete
        TagInputView.setup(tags)

        // add the small print to the view
        val smallPrintView = ButterKnife.findById<TextView>(view, R.id.small_print)
        rulesService.displayInto(smallPrintView)
        upload.setOnClickListener { v -> onUploadClicked() }
    }

    private fun onUploadClicked() {
        val file = file
        if (file == null) {
            showDialog(activity) {
                content(R.string.hint_upload_something_happen_try_again)
                positive(R.string.okay, { activity.finish() })
            }
            return
        }

        // we have an already started upload. lets just continue with it
        if (uploadInfo != null) {
            startUpload(file)
            return
        }

        uploadService.sizeOkay(file)
                .onErrorResumeNext(Observable.empty<Boolean>())
                .compose(bindToLifecycleAsync<Boolean>())
                .subscribe { sizeOkay ->
                    if (sizeOkay) {
                        startUpload(file)
                    } else {
                        handleSizeNotOkay()
                    }
                }
    }

    private fun startUpload(file: File) {
        checkMainThread()
        setFormEnabled(false)

        // get those from UI
        val type = selectedContentType
        val tags = Splitter.on(",").trimResults().omitEmptyStrings().split(this.tags.text.toString()).toSet()

        val uploadInfo = uploadInfo

        // start the upload
        val upload = if (uploadInfo == null) {
            busyIndicator.visibility = View.VISIBLE
            busyIndicator.progress = 0f
            uploadService.upload(file, type, tags)
        } else {
            busyIndicator.visibility = View.VISIBLE
            busyIndicator.spin()
            uploadService.post(uploadInfo, type, tags, false)
        }

        logger.info("Start upload of type {} with tags {}", type, tags)
        upload.compose(bindUntilEventAsync(FragmentEvent.DESTROY_VIEW))
                .doAfterTerminate { busyIndicator.visibility = View.GONE }
                .subscribe({ status ->
                    when {
                        status.finished -> {
                            logger.info("Finished! item id is {}", status.id)
                            onUploadComplete(status.id)

                        }
                        status.hasSimilar -> {
                            logger.info("Found similar posts. Showing them now")
                            this.uploadInfo = status
                            showSimilarPosts(status.similar)
                            setFormEnabled(true)

                        }
                        status.progress >= 0.99 -> {
                            logger.info("Uploading, progress is nearly finished")
                            if (!busyIndicator.isSpinning)
                                busyIndicator.spin()

                        }
                        status.progress >= 0 -> {
                            val progress = status.progress
                            logger.info("Uploading, progress is {}", progress)
                            busyIndicator.progress = progress

                        }
                        else -> {
                            logger.info("Upload finished, posting now.")
                            busyIndicator.spin()
                        }
                    }
                }, { this.onUploadError(it) })

        // scroll back up
        scrollView.fullScroll(View.FOCUS_UP)
    }

    private fun showSimilarPosts(similar: List<HasThumbnail>) {
        similarHintView.visibility = View.VISIBLE
        similarImages.visibility = View.VISIBLE
        similarImages.setThumbnails(similar)
        scrollView.requestChildFocus(view, view)
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
        logger.info("Go to new post now: {}", postId)

        val intent = Intent(activity, MainActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        intent.data = UriHelper.of(activity).post(FeedType.NEW, postId)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)

        activity.finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)

        logger.info("Got response from image picker: rc={}, intent={}", resultCode, intent)
        if (requestCode == RequestCodes.SELECT_MEDIA) {
            if (resultCode == Activity.RESULT_OK && intent != null) {
                val image = intent.data
                handleImageUri(image)
            } else {
                activity.finish()
            }
        }
    }

    @MainThread
    private fun handleImageUri(image: Uri) {
        checkMainThread()

        busyIndicator.visibility = View.VISIBLE

        logger.info("copy image to private memory")
        copy(activity, image)
                .compose(bindToLifecycleAsync())
                .subscribe({ this.onMediaFile(it) }, { this.onError(it) })
    }

    private fun onError(throwable: Throwable) {
        if (Throwables.getRootCause(throwable) is MediaNotSupported) {
            showCanNotHandleTypeDialog()
            return
        }

        showDialog(activity) {
            content(R.string.error_check_file_permission)
            positive(android.R.string.ok, { activity.finish() })
            onCancel { activity.finish() }
        }
    }

    private fun onUploadError(throwable: Throwable) {
        setFormEnabled(true)
        busyIndicator.visibility = View.GONE

        if (throwable is UploadService.UploadFailedException) {
            val causeText = getUploadFailureText(activity, throwable)
            showDialog(activity) {
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
        this.file = file

        logger.info("loading file into view.")
        val uri = MediaUri.of(-1, Uri.fromFile(file))

        fileMediaType = uri.mediaType

        val viewer = MediaViews.newInstance(MediaView.Config.of(activity, uri))
        viewer.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)

        RxView.attaches(viewer).subscribe { viewer.playMedia() }

        preview.removeAllViews()
        preview.addView(viewer)

        busyIndicator.visibility = View.GONE
    }

    private fun handleSizeNotOkay() {
        checkMainThread()
        if (fileMediaType == MediaUri.MediaType.IMAGE) {
            showDialog(activity) {
                content(R.string.hint_image_too_large)
                positive(R.string.down_size, { shrinkImage() })
                negative(android.R.string.no)
            }
        } else {
            showDialog(activity) {
                content(R.string.upload_hint_too_large)
                positive()
            }
        }
    }

    private fun shrinkImage() {
        uploadService.downsize(file!!)
                .compose(bindToLifecycleAsync<File>())
                .withBusyDialog(this)
                .subscribe(Action1 { newFile ->
                    handleImageUri(Uri.fromFile(newFile))
                    imageShrankSuccess()
                }, defaultOnError())
    }

    @MainThread
    private fun imageShrankSuccess() {
        Snackbar.make(checkNotNull<View>(view), R.string.hint_shrank_successful, Snackbar.LENGTH_LONG)
                .setAction(R.string.okay, {})
                .show()
    }

    private fun showCanNotHandleTypeDialog() {
        showDialog(activity) {
            content(R.string.upload_error_invalid_type)
            positive(R.string.okay, { activity.finish() })
        }
    }

    private val selectedContentType: ContentType
        get() {
            val types = ImmutableMap.builder<Int, ContentType>()
                    .put(R.id.upload_type_sfw, ContentType.SFW)
                    .put(R.id.upload_type_nsfw, ContentType.NSFW)
                    .put(R.id.upload_type_nsfl, ContentType.NSFL)
                    .put(R.id.upload_type_nsfp, ContentType.NSFP)
                    .build()

            val view = view
            if (view != null) {
                for ((key, value) in types) {
                    val button = ButterKnife.findById<RadioButton>(view, key)
                    if (button?.isChecked ?: false)
                        return value
                }
            }

            return ContentType.NSFL
        }

    private val urlArgument: Optional<Uri>
        get() {
            val arguments = arguments ?: return Optional.absent<Uri>()

            return Optional.fromNullable(arguments.getParcelable<Uri>(EXTRA_LOCAL_URI))
        }

    private class MediaNotSupported internal constructor() : RuntimeException("Media type not supported")

    companion object {
        private val logger = LoggerFactory.getLogger("UploadFragment")
        val EXTRA_LOCAL_URI = "UploadFragment.localUri"
        val EXTRA_MEDIA_TYPE = "UploadFragment.mediaType"

        @SuppressLint("NewApi")
        private fun copy(context: Context, source: Uri): Observable<File> {
            return Observable.fromCallable<File> {
                context.contentResolver.openInputStream(source).use { input ->
                    checkNotNull(input, "Could not open input stream")

                    // read the "header"
                    val bytes = ByteArray(512)
                    val count = ByteStreams.read(input, bytes, 0, bytes.size)

                    // and guess the type
                    var ext = MimeTypeHelper.guess(bytes)
                    if (ext.isPresent)
                        ext = MimeTypeHelper.extension(ext.get())

                    // fail if we couldnt get the type
                    if (!ext.isPresent)
                        throw MediaNotSupported()

                    val target = getTempFileUri(context, ext.get())

                    FileOutputStream(target).use { output ->
                        output.write(bytes, 0, count)

                        val copied = ByteStreams.copy(input, output)
                        logger.info("Copied {}kb", copied / 1024)
                    }

                    target
                }
            }
        }

        private fun getTempFileUri(context: Context, ext: String): File {
            return File(context.cacheDir, "upload." + ext)
        }

        private fun getUploadFailureText(context: Context, exception: UploadService.UploadFailedException): CharSequence {
            val reason = exception.errorCode
            val textId = ImmutableMap.builder<String, Int>()
                    .put("blacklisted", R.string.upload_error_blacklisted)
                    .put("internal", R.string.upload_error_internal)
                    .put("invalid", R.string.upload_error_invalid)
                    .put("download", R.string.upload_error_download_failed)
                    .put("exists", R.string.upload_error_exists)
                    .build()[reason]

            val text = Truss().append(context.getString(firstNonNull(textId, R.string.upload_error_unknown)))

            val report = exception.report
            if (report.isPresent) {
                val videoErrorId = ImmutableMap.builder<String, Int>()
                        .put("dimensionsTooSmall", R.string.upload_error_video_too_small)
                        .put("dimensionsTooLarge", R.string.upload_error_video_too_large)
                        .put("durationTooLong", R.string.upload_error_video_too_long)
                        .put("invalidCodec", R.string.upload_error_video_codec)
                        .put("invalidStreams", R.string.upload_error_video_streams)
                        .put("invalidContainer", R.string.upload_error_video_container)
                        .build()[report.get().error()]

                if (videoErrorId != null) {
                    text.append("\n\n")
                            .append(context.getString(R.string.upload_error_video), Truss.bold)
                            .append(" ")
                            .append(context.getString(videoErrorId))
                }

                text.append("\n\n")
                        .append("Info:\n", Truss.bold)
                        .append(context.getString(R.string.report_video_summary,
                                report.get().width(), report.get().height(),
                                report.get().format(), report.get().duration()))
                        .append("\n")

                val offset = context.resources.getDimensionPixelSize(R.dimen.bullet_list_leading_margin)
                for (stream in report.get().streams()) {
                    val streamInfo = context.getString(R.string.report_video_stream, stream.type(), stream.codec())
                    text.append(streamInfo,
                            BulletSpan(offset / 3),
                            LeadingMarginSpan.Standard(offset)).append("\n")
                }
            }

            return text.build()
        }
    }
}
