package com.pr0gramm.app.ui.views.viewer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.Stopwatch
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.services.InMemoryCacheService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.FancyExifThumbnailGenerator
import com.pr0gramm.app.ui.PreviewInfo
import com.pr0gramm.app.ui.base.AndroidCoroutineScope
import com.pr0gramm.app.ui.base.launchIgnoreErrors
import com.pr0gramm.app.ui.base.withBackgroundContext
import com.pr0gramm.app.ui.views.AspectImageView
import com.pr0gramm.app.ui.views.InjectorViewMixin
import com.pr0gramm.app.ui.views.instance
import com.pr0gramm.app.util.*
import com.squareup.picasso.Picasso
import com.trello.rxlifecycle.android.RxLifecycleAndroid
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import rx.Observable
import rx.functions.Action1
import rx.subjects.BehaviorSubject
import rx.subjects.ReplaySubject

/**
 */
abstract class MediaView(protected val config: MediaView.Config, @LayoutRes layoutId: Int?)
    : FrameLayout(config.activity), InjectorViewMixin, AndroidCoroutineScope {

    private val logger = if (BuildConfig.DEBUG) {
        Logger("MediaView[${config.mediaUri.id}]")
    } else {
        Logger("MediaView")
    }

    private val previewTarget = PreviewTarget(logger, this)
    private val onViewListener = BehaviorSubject.create<Void>()
    private val thumbnail = BehaviorSubject.create<Bitmap>()
    private val controllerView = ReplaySubject.create<View>()
    private val gestureDetector: GestureDetector

    private var mediaShown: Boolean = false

    protected val picasso: Picasso by instance()
    private val inMemoryCacheService: InMemoryCacheService by instance()
    private val fancyThumbnailGenerator: FancyExifThumbnailGenerator by instance()

    override var job: Job = SupervisorJob()
    override val androidContext: Context = context

    /**
     * Returns the url that this view should display.
     */
    protected val mediaUri: MediaUri

    var tapListener: TapListener? = null

    var isPlaying: Boolean = false
        private set

    var busyIndicator: View? = null
        private set

    var previewView: AspectImageView? = null
        private set

    /**
     * Sets the aspect ratio of this view. Will be ignored, if not positive and size
     * is then estimated from the children. If aspect is provided, the size of
     * the view is estimated from its parents width.
     */
    open var viewAspect = -1f
        set(viewAspect) {
            previewView?.aspect = if (viewAspect > 0) Math.max(viewAspect, MIN_PREVIEW_ASPECT) else -1f

            if (this.viewAspect != viewAspect) {
                field = viewAspect
                requestLayout()
            }
        }

    init {
        this.mediaUri = config.mediaUri

        layoutParams = DEFAULT_PARAMS
        if (layoutId != null) {
            LayoutInflater.from(config.activity).inflate(layoutId, this)

            previewView = findOptional(R.id.preview)
            busyIndicator = findOptional(R.id.busy_indicator)
        }

        // register the detector to handle double taps
        gestureDetector = GestureDetector(config.activity, GestureListener())

        showPreloadedIndicator()

        addOnDetachListener {
            if (isPlaying)
                stopMedia()

            // no more views will come now.
            controllerView.onCompleted()
            onViewListener.onCompleted()
            thumbnail.onCompleted()
        }

        if (hasPreviewView() && config.previewInfo?.fullThumbUri != null) {
            addOnAttachListener {
                // test if we need to request the thumby preview.
                if (hasPreviewView()) {

                    logger.debug { "Requesting full preview image at (${config.previewInfo.fullThumbUri})." }
                    RxPicasso.load(picasso, picasso.load(config.previewInfo.fullThumbUri).noPlaceholder())
                            .onErrorResumeEmpty()
                            .compose(RxLifecycleAndroid.bindView(this))
                            .subscribe(previewTarget)
                }
            }
        }

        // set preview info
        config.previewInfo?.let { updatePreview(it) }
    }

    /**
     * An observable that produces a value on the main thread if the video was seen.
     */
    fun viewed(): Observable<Void> {
        return onViewListener
    }

    override fun onDetachedFromWindow() {
        job.cancel()
        super.onDetachedFromWindow()
    }

    @SuppressLint("SetTextI18n")
    private fun showPreloadedIndicator() {
        if (BuildConfig.DEBUG && mediaUri.isLocalFile) {
            val preloadHint = TextView(context)
            preloadHint.text = "preloaded"
            preloadHint.layoutParams = DEFAULT_PARAMS
            preloadHint.setTextColor(context.getColorCompat(ThemeHelper.accentColor))
            addView(preloadHint)
        }
    }

    protected fun <T> bindView(): Observable.Transformer<T, T> {
        return RxLifecycleAndroid.bindView<T>(this)
    }

    protected fun <T> backgroundBindView(): Observable.Transformer<T, T> {
        return Observable.Transformer {
            it.subscribeOn(BackgroundScheduler)
                    .unsubscribeOn(BackgroundScheduler)
                    .observeOn(MainThreadScheduler)
                    .compose(RxLifecycleAndroid.bindView<T>(this))
        }
    }

    /**
     * Sets the pixels image for this media view. You need to provide a width and height.
     * Those values will be used to place the pixels image correctly.
     */
    private fun updatePreview(info: PreviewInfo) {
        if (!hasPreviewView())
            return

        if (info.width > 0 && info.height > 0) {
            viewAspect = info.aspect
        }

        info.fancy?.valueOrNull?.let { preview ->
            logger.debug { "Using provided fancy preview image." }
            val drawable = BitmapDrawable(resources, preview)
            setPreviewDrawable(drawable)
            thumbnail.onNext(preview)
            return
        }

        info.preview?.let { preview ->
            logger.debug { "Using provided preview image." }
            setPreviewDrawable(preview)

            if (preview is BitmapDrawable) {
                val bitmap = preview.bitmap
                thumbnail.onNext(bitmap)
            }
        }

        launchIgnoreErrors {
            val fancyPreview = info.fancy?.get() ?: run {
                withBackgroundContext {
                    logger.debug { "Requesting fancy thumbnail on background thread now." }
                    fancyThumbnailGenerator.fancyThumbnail(info.previewUri, info.aspect)
                }
            }

            previewTarget.call(fancyPreview)
        }
    }

    private fun hasPreviewView(): Boolean {
        return previewView != null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (this.viewAspect <= 0) {
            // If we dont have a view aspect, we will let the content decide
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        } else {
            val heightUnspecified = View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED

            // we shouldn't get larger than this.
            val maxWidth = View.MeasureSpec.getSize(widthMeasureSpec)
            val maxHeight = View.MeasureSpec.getSize(heightMeasureSpec)

            val width: Int
            val height: Int
            if (heightUnspecified || maxWidth / maxHeight.toDouble() < this.viewAspect) {
                width = maxWidth
                height = (maxWidth / this.viewAspect).toInt() + paddingTop + paddingBottom
            } else {
                width = (maxHeight * this.viewAspect).toInt()
                height = maxHeight
            }

            // use the calculated sizes!
            setMeasuredDimension(width, height)
            measureChildren(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        }
    }

    /**
     * Removes the preview image from this view.
     * This will not remove the view until the transition ended.
     */
    fun removePreviewImage() {
        previewView.removeFromParent()
        previewView = null

        onPreviewRemoved()
    }

    protected open fun onPreviewRemoved() {
        // implement action that should be executed
        // the moment the preview is removed
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event)
    }

    /**
     * The listener that handles double tapping
     */
    private inner class GestureListener : SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            return this@MediaView.onDoubleTap(e)
        }

        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            return onSingleTap(event)
        }
    }

    protected open fun onDoubleTap(event: MotionEvent): Boolean {
        return tapListener?.onDoubleTap(event) ?: false

    }

    protected open fun onSingleTap(event: MotionEvent): Boolean {
        return tapListener?.onSingleTap(event) ?: false
    }

    /**
     * Displays an indicator that something is loading. You need to pair
     * this with a call to [.hideBusyIndicator]
     */
    protected fun showBusyIndicator() {
        if (busyIndicator != null) {
            if (busyIndicator!!.parent == null)
                addView(busyIndicator)

            busyIndicator!!.visibility = View.VISIBLE
        }
    }

    protected fun showBusyIndicator(show: Boolean) {
        if (show) {
            showBusyIndicator()
        } else {
            hideBusyIndicator()
        }
    }

    /**
     * Hides the busy indicator that was shown in [.showBusyIndicator].
     */
    protected fun hideBusyIndicator() {
        busyIndicator.removeFromParent()
    }

    /**
     * Gets the effective uri that should be downloaded
     */
    protected val effectiveUri: Uri
        get() = mediaUri.baseUri

    open fun playMedia() {
        logger.debug { "Should start playing media" }
        isPlaying = true

        if (mediaShown) {
            onMediaShown()
        }
    }

    open fun stopMedia() {
        logger.debug { "Should stop playing media" }
        isPlaying = false
    }

    protected open fun onMediaShown() {
        removePreviewImage()
        mediaShown = true

        if (isPlaying && onViewListener != null) {
            if (!onViewListener.hasCompleted()) {
                onViewListener.onNext(null)
                onViewListener.onCompleted()
            }
        }
    }

    open fun rewind() {
        // do nothing by default
    }

    open val actualMediaView: MediaView
        get() = this

    override fun setLayoutParams(params: ViewGroup.LayoutParams) {
        super.setLayoutParams(params)

        // forward the gravity to the preview if possible
        if (params is FrameLayout.LayoutParams) {
            previewView?.apply {
                (layoutParams as FrameLayout.LayoutParams).gravity = params.gravity
            }
        }
    }

    fun setPreviewDrawable(previewDrawable: Drawable) {
        this.previewView?.setImageDrawable(previewDrawable)
    }

    protected fun hasAudio(): Boolean {
        return config.audio
    }

    protected fun publishControllerView(view: View) {
        controllerView.onNext(view)
    }

    fun controllerView(): Observable<View> {
        return controllerView
    }

    fun thumbnail(): Observable<Bitmap> {
        return thumbnail
    }

    interface TapListener {
        fun onSingleTap(event: MotionEvent): Boolean

        fun onDoubleTap(event: MotionEvent): Boolean
    }

    private class PreviewTarget(private val logger: Logger, mediaView: MediaView) : Action1<Bitmap> {
        private val mediaView by weakref(mediaView)
        private val watch = Stopwatch()

        override fun call(bitmap: Bitmap) {
            logger.debug { "Got a preview image after $watch" }

            this.mediaView?.let { mediaView ->
                if (mediaView.previewView != null) {
                    val nextImage = BitmapDrawable(mediaView.resources, bitmap)
                    mediaView.setPreviewDrawable(nextImage)
                }

                mediaView.thumbnail.onNext(bitmap)
            }
        }
    }

    data class Config(val activity: Activity, val mediaUri: MediaUri,
                      val previewInfo: PreviewInfo? = null,
                      val audio: Boolean = false) {

        companion object {
            fun ofFeedItem(activity: Activity, item: FeedItem): Config {
                return Config(activity,
                        mediaUri = MediaUri.of(activity, item),
                        audio = item.audio,
                        previewInfo = PreviewInfo.of(activity, item))
            }
        }
    }

    companion object {
        private const val MIN_PREVIEW_ASPECT = 1 / 30.0f

        internal const val ANIMATION_DURATION = 500L

        private val DEFAULT_PARAMS = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL)
    }
}

