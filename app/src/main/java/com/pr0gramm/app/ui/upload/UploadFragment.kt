package com.pr0gramm.app.ui.upload

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.BulletSpan
import android.text.style.LeadingMarginSpan
import android.view.View
import android.view.ViewGroup
import android.widget.Checkable
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.RadioButton
import androidx.core.text.bold
import androidx.core.text.inSpans
import androidx.core.view.children
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import com.pr0gramm.app.R
import com.pr0gramm.app.RequestCodes
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.databinding.FragmentUploadBinding
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.services.RulesService
import com.pr0gramm.app.services.UploadService
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.*
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.base.bindViews
import com.pr0gramm.app.ui.base.launchInViewScope
import com.pr0gramm.app.ui.base.launchUntilViewDestroy
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment
import com.pr0gramm.app.ui.views.viewer.MediaUri
import com.pr0gramm.app.ui.views.viewer.MediaView
import com.pr0gramm.app.ui.views.viewer.MediaViews
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.instance
import kotlinx.coroutines.flow.*

/**
 * This activity performs the actual upload.
 */
class UploadFragment : BaseFragment("UploadFragment", R.layout.fragment_upload) {
    private val rulesService: RulesService by instance()

    private val tagSuggestions: TagSuggestionService by instance()
    private val userService: UserService by instance()

    private val views by bindViews(FragmentUploadBinding::bind)

    // private var file: File? = null
    // private var fileMediaType: MediaUri.MediaType? = null
    // private var uploadInfo: UploadService.State.Uploaded? = null

    private var urlArgument: Uri? by optionalFragmentArgument()
    private var mediaTypeArgument: String? by optionalFragmentArgument(default = "image/*")

    private val vm by viewModels {
        UploadViewModel(
                context = requireContext().applicationContext,
                uploadService = instance(),
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentState = vm.state.value

        if (!currentState.hasSource && savedInstanceState == null) {
            val uri = urlArgument

            if (uri != null) {
                handleImageUri(uri)
            } else {
                handleImagePickRequest()
            }
        }

        // enable auto-complete
        tagSuggestions.setupView(views.tags)

        // add the small print to the view
        launchUntilViewDestroy {
            rulesService.displayInto(view.find(R.id.small_print))
        }

        // react on change in the tag input window
        views.tags.addTextChangedListener { text ->
            views.tagOpinionHint.isVisible = tagSuggestions.containsQuestionableTag(text)
        }

        views.actionShrink.setOnClickListener {
            vm.onShrinkImageClicked()
        }

        views.actionUpload.setOnClickListener {
            val contentType = selectedContentType() ?: ContentType.NSFL
            vm.onUploadClicked(views.tags.text.toString(), contentType)

            // scroll up if the user clicks on the upload button
            views.scrollView.fullScroll(View.FOCUS_UP)
        }

        val types = listOf(
                R.id.upload_type_sfw,
                R.id.upload_type_nsfp,
                R.id.upload_type_nsfw,
                R.id.upload_type_nsfl,
        )

        for (viewId in types) {
            view.findOptional<CompoundButton>(viewId)?.setOnCheckedChangeListener { _, _ ->
                updateFormState(vm.state.value)
            }
        }

        launchInViewScope {
            userService.loginStates.drop(1).collect {
                updateFormState(vm.state.value)
            }
        }

        launchInViewScope {
            vm.state.collect { state ->
                logger.debug { "Current state is: $state" }

                state.error?.consume { error -> handleStateError(error) }


                displayBusyState(state)
                displayState(state)
                updateFormState(state)
            }
        }

        launchInViewScope {
            vm.state.distinctUntilChangedBy { state -> state.mediaUri }.collect { state ->
                displayCurrentMedia(state)
            }
        }

        launchInViewScope {
            val state = vm.state.first { state -> state.postId != null }
            onUploadComplete(state.postId!!)
        }

        launchInViewScope {
            val fSimilarItems = vm.state
                    .mapNotNull { state -> state.uploadState as? UploadService.State.SimilarItems }
                    .distinctUntilChanged()

            fSimilarItems.collect { similarItems ->
                if (similarItems.items.isNotEmpty()) {
                    showSimilarPosts(similarItems.items)
                }
            }
        }
    }

    private fun displayState(state: UploadViewModel.State) {
        if (state.fileSizeOkay) {
            views.shrinkContainer.isVisible = false
        } else {
            views.shrinkContainer.isVisible = true

            if (state.mediaUri?.mediaType == MediaUri.MediaType.IMAGE) {
                views.shrinkText.text = getString(R.string.hint_shrink_image)
                views.actionShrink.isVisible = true
            } else {
                views.shrinkText.text = getString(R.string.hint_shrink_video)
                views.actionShrink.isVisible = false
            }
        }

        views.actionShrink.isEnabled = !state.busy

        state.imageWasShrunken?.consume {
            imageWasShrunken()
        }
    }

    private fun displayBusyState(state: UploadViewModel.State) {
        views.busyContainer.isVisible = state.busy

        if (state.busyText != null) {
            views.busyState.isVisible = true
            views.busyState.text = state.busyText
        } else {
            views.busyState.isVisible = false
        }

        if (state.busy) {
            val progress = (state.uploadState as? UploadService.State.Uploading)?.progress

            if (state.uploading && progress != null && progress < 0.99) {
                if (views.busyIndicator.isSpinning) {
                    views.busyIndicator.stopSpinning()
                    views.busyIndicator.setLinearProgress(true)
                }

                views.busyIndicator.progress = progress
            } else {
                if (!views.busyIndicator.isSpinning) {
                    views.busyIndicator.spin()
                }
            }
        }
    }

    private fun handleImagePickRequest() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = mediaTypeArgument

        // check if someone can handle this intent
        if (context?.canStartIntent(intent) == false) {
            showDialog(this) {
                content(R.string.error_no_gallery_app)
                positive { activity?.finish() }
            }

            return
        }

        startActivityForResult(intent, RequestCodes.SELECT_MEDIA)
    }

    private fun showSimilarPosts(similar: List<Api.Posted.SimilarItem>) {
        views.similarHint.isVisible = true
        views.similarImages.isVisible = true
        views.similarImages.items = similar

        views.similarHint.requestFocus()
    }

    private fun updateFormState(state: UploadViewModel.State) {
        val enabled = !state.busy && state.error == null

        views.tags.isEnabled = enabled

        val loginState = userService.loginState

        for (childView in views.contentTypeGroup.children) {
            childView.isEnabled = enabled

            if (!loginState.verified && childView.id != R.id.upload_type_sfw) {
                childView.isEnabled = false
            }
        }

        val hasTagSelected = views.contentTypeGroup.children.any { childView ->
            childView is Checkable && childView.isChecked
        }

        val imageSizeOkay = state.fileSizeOkay
        val isVerifiedOrUploadTypeIsSFW = selectedContentType() == ContentType.SFW || loginState.verified

        views.actionUpload.isEnabled = enabled && hasTagSelected && imageSizeOkay && isVerifiedOrUploadTypeIsSFW
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

    private fun handleImageUri(image: Uri) {
        vm.onImageSelected(image)
    }

    private fun handleStateError(err: Exception) {
        when {
            err.rootCause is UploadViewModel.MediaNotSupportedException -> {
                showDialog(this) {
                    content(R.string.upload_error_invalid_type)
                    positive(R.string.okay) { activity?.finish() }
                    onCancel { activity?.finish() }
                }
            }

            err is UploadService.UploadFailedException -> {
                val causeText = getUploadFailureText(err)
                showDialog(this) {
                    content(causeText)
                    positive { activity?.finish() }
                    onCancel { activity?.finish() }
                }
            }

            err is UploadViewModel.CopyMediaException -> {
                AndroidUtility.logToCrashlytics(err, force = true)

                showDialog(this) {
                    content(R.string.error_check_file_permission)
                    positive(android.R.string.ok) { activity?.finish() }
                    onCancel { activity?.finish() }
                }
            }

            else -> {
                AndroidUtility.logToCrashlytics(err)

                val str = ErrorFormatting.getFormatter(err).getMessage(requireActivity(), err)
                ErrorDialogFragment.showErrorString(parentFragmentManager, str)
            }
        }
    }

    private fun displayCurrentMedia(state: UploadViewModel.State) {
        val activity = activity ?: return

        if (state.mediaUri == null) {
            views.preview.removeAllViews()
            views.preview.setBackgroundResource(R.color.grey_800)
            return
        }

        views.preview.background = null

        logger.info { "Loading mediaItem into view: ${state.file}" }

        val viewer = MediaViews.newInstance(MediaView.Config(activity, state.mediaUri))

        viewer.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)

        viewer.addOnAttachListener { viewer.playMedia() }

        views.preview.removeAllViews()
        views.preview.addView(viewer)
    }

    private fun imageWasShrunken() {
        view?.let { view ->
            Snackbar.make(view, R.string.hint_shrank_successful, Snackbar.LENGTH_LONG)
                    .configureNewStyle()
                    .setAction(R.string.okay) {}
                    .show()
        }
    }

    private fun selectedContentType(): ContentType? {
        val types = mapOf(
                R.id.upload_type_sfw to ContentType.SFW,
                R.id.upload_type_nsfp to ContentType.NSFP,
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

        // fallback, this should not happen.
        return null
    }

    private fun getUploadFailureText(exception: UploadService.UploadFailedException): CharSequence {
        val textId = when (exception.errorCode) {
            "blacklisted" -> R.string.upload_error_blacklisted
            "internal" -> R.string.upload_error_internal
            "invalid" -> R.string.upload_error_invalid
            "download" -> R.string.upload_error_download_failed
            "exists" -> R.string.upload_error_exists
            else -> R.string.upload_error_unknown
        }

        return SpannableStringBuilder().apply {
            append(getString(textId))

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
                            .bold { append(getString(R.string.upload_error_video)) }
                            .append(" ")
                            .append(getString(videoErrorId))
                }

                append("\n\n")
                        .bold { append("Info:\n") }
                        .append(getString(R.string.report_video_summary,
                                report.width, report.height,
                                report.format, report.duration))
                        .append("\n")

                val offset = resources.getDimensionPixelSize(R.dimen.bullet_list_leading_margin)
                for (stream in report.streams) {
                    val streamInfo = getString(R.string.report_video_stream,
                            stream.type, stream.codec ?: "null")

                    inSpans(BulletSpan(offset / 3)) {
                        inSpans(LeadingMarginSpan.Standard(offset)) {
                            append(streamInfo)
                            append("\n")
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun forLocalUri(url: Uri?) = UploadFragment().apply { urlArgument = url }

        fun forMediaType(type: String?) = UploadFragment().apply { mediaTypeArgument = type }
    }
}
