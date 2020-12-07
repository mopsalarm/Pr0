package com.pr0gramm.app.ui.upload

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.services.MimeTypeHelper
import com.pr0gramm.app.services.UploadService
import com.pr0gramm.app.ui.fragments.feed.ConsumableValue
import com.pr0gramm.app.ui.fragments.feed.update
import com.pr0gramm.app.ui.views.viewer.MediaUri
import com.pr0gramm.app.util.BoundedInputStream
import com.pr0gramm.app.util.checkMainThread
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class UploadViewModel(
        private val context: Context,
        private val uploadService: UploadService,
) : ViewModel() {

    private val logger = Logger("UploadViewModel")

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState

    fun onImageSelected(image: Uri) {
        logger.info { "copy image to private memory" }

        viewModelScope.launch {
            // prevent races, do not do anything if we already have a source image
            if (state.value.hasSource) {
                return@launch
            }

            mutableState.update { previousState ->
                previousState.copy(hasSource = true, busy = true)
            }

            copyUriToStaging(image)
        }
    }

    fun onShrinkImageClicked(): Job {
        return viewModelScope.launch {
            val file = state.value.file ?: return@launch

            mutableState.update { previousState ->
                previousState.copy(busy = true)
            }

            try {
                val newFile = uploadService.shrinkImage(file)

                // this resets the busy flag
                copyUriToStaging(Uri.fromFile(newFile))

                // remove the temporary file
                runInterruptible(Dispatchers.IO) {
                    newFile.delete()
                }

                mutableState.update { previousState ->
                    previousState.copy(imageWasShrunken = ConsumableValue(true))
                }

            } catch (err: Exception) {
                mutableState.update { previousState ->
                    previousState.copy(busy = false, error = ConsumableValue(err))
                }
            }
        }
    }

    fun onUploadClicked(tagsString: String, contentType: ContentType) {
        startUpload(state.value.file ?: return, tagsString, contentType)
    }

    private fun startUpload(file: File, tagsString: String, contentType: ContentType) {
        checkMainThread()

        mutableState.update { previousState ->
            previousState.copy(busy = true, uploading = true)
        }

        val tags = tagsString.split('#', ',')
                .map { tag -> tag.trim() }
                .filter { tag -> tag.isNotEmpty() }
                .toSet()

        viewModelScope.launch {
            logger.info { "Start upload of type $contentType with tags $tags" }

            val uploadStates = when (val key = state.value.uploadKey) {
                is String -> {
                    // continue previous upload, just post it!
                    uploadService.post(key, contentType, tags, false)
                }
                else -> {
                    uploadService.upload(file, contentType, tags)
                }
            }

            try {
                uploadStates.collect { state ->
                    handleUploadState(state)
                }

            } catch (err: Exception) {
                mutableState.update { previousState ->
                    previousState.copy(error = ConsumableValue(err))
                }
            }

            mutableState.update { previousState ->
                previousState.copy(busy = false, uploading = false)
            }
        }
    }

    private fun toBusyText(state: UploadService.State): String? {
        return when (state) {
            is UploadService.State.Processing -> context.getString(R.string.upload_state_processing)
            is UploadService.State.Uploading -> context.getString(R.string.upload_state_uploading)
            is UploadService.State.Pending -> context.getString(R.string.upload_state_pending, state.position)
            is UploadService.State.Uploaded -> context.getString(R.string.upload_state_uploaded)
            else -> null
        }
    }

    private fun handleUploadState(state: UploadService.State) {
        val previousState = this.state.value

        logger.debug { "Got new upload state: $state" }

        val newValue = when (state) {
            is UploadService.State.Uploaded -> {
                logger.info { "Got upload key '$state.key', storing." }
                previousState.copy(uploading = false, uploadKey = state.key)
            }

            is UploadService.State.SimilarItems -> {
                logger.info { "Found similar posts. Showing them now" }
                previousState.copy(busy = false, uploading = false)
            }

            is UploadService.State.Error -> {
                previousState.copy(
                        busy = false, uploading = false,
                        error = ConsumableValue(UploadService.UploadFailedException(state.error, state.report)),
                )
            }

            is UploadService.State.Success -> {
                logger.info { "Finished! item id is ${state.id}" }
                previousState.copy(
                        busy = false, uploading = false,
                        postId = state.id,
                )
            }

            is UploadService.State.Uploading -> {
                previousState
            }

            else -> {
                logger.info { "Unhandled upload state: $state" }
                previousState
            }
        }

        mutableState.value = newValue.copy(
                busyText = toBusyText(state),
                uploadState = state,
        )
    }

    private suspend fun copyUriToStaging(image: Uri) {
        try {
            val copied = copyToTemp(image)

            val sizeOkay = uploadService.sizeOkay(copied)

            mutableState.update { previousState ->
                previousState.copy(busy = false, file = copied, fileSizeOkay = sizeOkay)
            }

        } catch (err: CancellationException) {
            throw err
            // ignored

        } catch (err: Exception) {
            mutableState.update { previousState ->
                previousState.copy(busy = false, error = ConsumableValue(CopyMediaException(err)))
            }
        }
    }

    private suspend fun copyToTemp(source: Uri): File {
        return runInterruptible(Dispatchers.IO) {
            context.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input) { "Could not open input stream" }

                // read the first few bytes as "header"
                val bytes = BoundedInputStream(input, 512).readBytes()

                // and guess the type, also fail if we couldn't get
                // an extension (and thereby a type)
                val ext = MimeTypeHelper.guess(bytes)
                        ?.let { MimeTypeHelper.extension(it) }
                        ?: throw MediaNotSupportedException()


                logger.info { "Creating temporary file with extension '$ext'" }
                val target = makeTempUploadFile(context, ext)

                FileOutputStream(target).use { output ->
                    output.write(bytes)

                    val maxSize = 1024 * 1024 * 48L
                    val copied = BoundedInputStream(input, maxSize).copyTo(output)
                    logger.info { "Copied ${copied / 1024}kb" }

                    if (copied == maxSize) {
                        throw IOException("File too large.")
                    }
                }

                return@runInterruptible target
            }
        }
    }

    private fun makeTempUploadFile(context: Context, ext: String): File {
        return File(context.cacheDir, "upload.${System.currentTimeMillis()}.$ext").also { file ->
            // cleanup if the application exits
            file.deleteOnExit()
        }
    }

    data class State(
            val busy: Boolean = true,
            val busyText: String? = null,

            val uploading: Boolean = false,
            val uploadKey: String? = null,
            val uploadState: UploadService.State? = null,

            // already has a source uri set
            val hasSource: Boolean = false,

            val file: File? = null,
            val fileSizeOkay: Boolean = true,
            val imageWasShrunken: ConsumableValue<Boolean>? = null,

            val error: ConsumableValue<Exception>? = null,

            val postId: Long? = null,
    ) {
        // parse a media uri from the file
        val mediaUri: MediaUri? = file?.let { file -> MediaUri.of(-1, Uri.fromFile(file)) }
    }

    class CopyMediaException(err: Exception) : RuntimeException("Cannot read media", err)

    class MediaNotSupportedException : RuntimeException("Media type not supported")
}