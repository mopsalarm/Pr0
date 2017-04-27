package com.pr0gramm.app.ui.views.viewer

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.support.annotation.LayoutRes
import android.support.v4.content.ContextCompat
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.widget.FrameLayout
import android.widget.TextView
import com.github.salomonbrys.kodein.instance
import com.google.common.base.Stopwatch
import com.jakewharton.rxbinding.view.RxView
import com.jakewharton.rxbinding.view.attachEvents
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.services.InMemoryCacheService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.FancyExifThumbnailGenerator
import com.pr0gramm.app.ui.PreviewInfo
import com.pr0gramm.app.ui.views.AspectImageView
import com.pr0gramm.app.ui.views.KodeinViewMixin
import com.pr0gramm.app.util.*
import com.squareup.picasso.Picasso
import com.trello.rxlifecycle.android.RxLifecycleAndroid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.functions.Action1
import rx.subjects.BehaviorSubject
import rx.subjects.ReplaySubject

/**
 */
abstract class MediaView(protected val config: MediaView.Config, @LayoutRes layoutId: Int?) : FrameLayout(config.activity), KodeinViewMixin {
    private val logger = if (BuildConfig.DEBUG) {
        LoggerFactory.getLogger("MediaView[${config.mediaUri.id}]")
    } else {
        LoggerFactory.getLogger("MediaView")
    }

    private val previewTarget = PreviewTarget(logger, this)
    private val onViewListener = BehaviorSubject.create<Void>()
    private val thumbnail = BehaviorSubject.create<Bitmap>()
    private val controllerView = ReplaySubject.create<View>()
    private val gestureDetector: GestureDetector

    private var mediaShown: Boolean = false
    private var compatClipBounds: Rect? = null

    protected val picasso: Picasso = instance()
    private val inMemoryCacheService: InMemoryCacheService = instance()
    private val fancyThumbnailGenerator: FancyExifThumbnailGenerator = instance()

    /**
     * Returns the url that this view should display.
     */
    protected val mediaUri: MediaUri

    var tapListener: TapListener? = null

    var isResumed: Boolean = false
        private set

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

            previewView = findOptional<AspectImageView>(R.id.preview)
            busyIndicator = findOptional<View>(R.id.busy_indicator)
        }

        // register the detector to handle double taps
        gestureDetector = GestureDetector(config.activity, GestureListener())

        showPreloadedIndicator()

        RxView.detaches(this).subscribe {
            if (isPlaying)
                stopMedia()

            if (isResumed)
                onPause()

            // no more views will come now.
            controllerView.onCompleted()
            onViewListener.onCompleted()
            thumbnail.onCompleted()
        }

        if (hasPreviewView() && ThumbyService.isEligibleForPreview(mediaUri)) {
            attachEvents().limit(1).subscribe {
                // test if we need to request the thumby preview.
                if (hasPreviewView()) {
                    val uri = ThumbyService.thumbUri(mediaUri)

                    logger.debug("Requesting thumby preview image.")
                    RxPicasso.load(picasso, picasso.load(uri).noPlaceholder())
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

    @SuppressLint("SetTextI18n")
    private fun showPreloadedIndicator() {
        if (BuildConfig.DEBUG && mediaUri.isLocal) {
            val preloadHint = TextView(context)
            preloadHint.text = "preloaded"
            preloadHint.layoutParams = DEFAULT_PARAMS
            preloadHint.setTextColor(ContextCompat.getColor(context, ThemeHelper.accentColor))
            addView(preloadHint)
        }
    }

    protected fun <T> bindView(): Observable.Transformer<T, T> {
        return RxLifecycleAndroid.bindView<T>(this)
    }

    protected fun <T> backgroundBindView(): Observable.Transformer<T, T> {
        return Observable.Transformer {
            it.subscribeOn(BackgroundScheduler.instance())
                    .unsubscribeOn(BackgroundScheduler.instance())
                    .observeOn(AndroidSchedulers.mainThread())
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
            logger.debug("Using provided fancy preview image.")
            val drawable = BitmapDrawable(resources, preview)
            setPreviewDrawable(drawable)
            thumbnail.onNext(preview)
            return
        }

        info.preview?.let { preview ->
            logger.debug("Using provided preview image.")
            setPreviewDrawable(preview)

            if (preview is BitmapDrawable) {
                val bitmap = preview.bitmap
                thumbnail.onNext(bitmap)
            }
        }

        val rxFancyPreviewImage = info.fancy?.asObservable() ?: Observable.fromCallable {
            logger.debug("Requesting fancy thumbnail on background thread now.")
            fancyThumbnailGenerator.fancyThumbnail(info.previewUri, info.aspect)
        }.subscribeOn(BackgroundScheduler.instance())

        rxFancyPreviewImage
                .onErrorResumeNext { err ->
                    logger.warn("Could not generate fancy thumbnail", err)
                    Observable.empty()
                }
                .observeOnMain()
                .compose(backgroundBindView())
                .subscribe(previewTarget)
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

            // we shouldnt get larger than this.
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
            return this@MediaView.onDoubleTap()
        }

        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            return onSingleTap(event)
        }
    }

    protected fun onDoubleTap(): Boolean {
        return tapListener?.onDoubleTap() ?: false

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

    open fun onPause() {
        isResumed = false
    }

    open fun onResume() {
        isResumed = true
    }

    /**
     * Gets the effective uri that should be downloaded
     */
    protected val effectiveUri: Uri
        get() = mediaUri.baseUri

    open fun playMedia() {
        logger.info("Should start playing media")
        isPlaying = true

        if (mediaShown) {
            onMediaShown()
        }
    }

    open fun stopMedia() {
        logger.info("Should stop playing media")
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

    override fun dispatchDraw(canvas: Canvas) {
        drawWithClipBounds(canvas, { super.dispatchDraw(it) })
    }

    @SuppressLint("MissingSuperCall")
    override fun draw(canvas: Canvas) {
        drawWithClipBounds(canvas, { super.draw(it) })
    }

    private fun drawWithClipBounds(canvas: Canvas, action: (Canvas) -> Unit) {
        if (compatClipBounds != null) {
            canvas.save()

            // clip and draw!
            canvas.clipRect(compatClipBounds!!)
            action(canvas)

            canvas.restore()

        } else {
            action(canvas)
        }
    }

    fun setClipBoundsCompat(clipBounds: Rect?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setClipBounds(clipBounds)

        } else if (this.compatClipBounds !== clipBounds) {
            this.compatClipBounds = clipBounds
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                setClipBounds(clipBounds)
            } else {
                invalidate()
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

        fun onDoubleTap(): Boolean
    }

    private class PreviewTarget(private val logger: Logger, mediaView: MediaView) : Action1<Bitmap> {
        private val mediaView by weakref(mediaView)
        private val watch = Stopwatch.createStarted()

        override fun call(bitmap: Bitmap) {
            logger.debug("Got a preview image after {}", watch)

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
            @JvmStatic
            fun of(activity: Activity, uri: MediaUri): Config {
                return Config(activity, uri)
            }

            @JvmStatic
            fun ofFeedItem(activity: Activity, item: FeedItem): Config {
                return Config(activity,
                        mediaUri = MediaUri.of(activity, item),
                        audio = item.audio,
                        previewInfo = PreviewInfo.of(activity, item))
            }
        }
    }

    companion object {
        private val MIN_PREVIEW_ASPECT = 1 / 30.0f

        const internal val ANIMATION_DURATION = 500

        private val DEFAULT_PARAMS = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL)
    }
}

