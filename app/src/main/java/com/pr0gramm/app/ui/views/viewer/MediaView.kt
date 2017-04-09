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
import butterknife.ButterKnife
import com.jakewharton.rxbinding.view.RxView
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Dagger
import com.pr0gramm.app.R
import com.pr0gramm.app.services.InMemoryCacheService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.FancyExifThumbnailGenerator
import com.pr0gramm.app.ui.PreviewInfo
import com.pr0gramm.app.ui.views.AspectImageView
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.BackgroundScheduler
import com.pr0gramm.app.util.RxPicasso
import com.squareup.picasso.Picasso
import com.trello.rxlifecycle.android.RxLifecycleAndroid
import org.slf4j.LoggerFactory
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.functions.Action1
import rx.subjects.BehaviorSubject
import rx.subjects.ReplaySubject
import java.lang.ref.WeakReference
import javax.inject.Inject

/**
 */
abstract class MediaView(protected val config: MediaView.Config, @LayoutRes layoutId: Int?) : FrameLayout(config.activity) {
    private val previewTarget = PreviewTarget(this)
    private val onViewListener = BehaviorSubject.create<Void>()
    private val thumbnail = BehaviorSubject.create<Bitmap>()
    private val gestureDetector: GestureDetector

    /**
     * Returns the url that this view should display.
     */
    protected val mediaUri: MediaUri

    private var mediaShown: Boolean = false

    var tapListener: TapListener? = null

    var isResumed: Boolean = false
        private set

    var isPlaying: Boolean = false
        private set

    private val controllerView = ReplaySubject.create<View>()

    private var compatClipBounds: Rect? = null

    /**
     * Sets the aspect ratio of this view. Will be ignored, if not positive and size
     * is then estimated from the children. If aspect is provided, the size of
     * the view is estimated from its parents width.
     */
    open var viewAspect = -1f
        set(viewAspect) {
            previewView?.setAspect(if (viewAspect > 0) Math.max(viewAspect, MIN_PREVIEW_ASPECT) else -1f)

            if (this.viewAspect != viewAspect) {
                field = viewAspect
                requestLayout()
            }
        }

    var busyIndicator: View? = null
        private set

    var previewView: AspectImageView? = null
        internal set

    @Inject
    internal lateinit var picasso: Picasso

    @Inject
    internal lateinit var inMemoryCacheService: InMemoryCacheService

    @Inject
    internal lateinit var fancyThumbnailGenerator: FancyExifThumbnailGenerator

    init {
        // inject all the stuff!
        injectComponent(Dagger.activityComponent(config.activity))

        this.mediaUri = config.mediaUri

        layoutParams = DEFAULT_PARAMS
        if (layoutId != null) {
            LayoutInflater.from(config.activity).inflate(layoutId, this)
            ButterKnife.bind(this)

            previewView = ButterKnife.findById<AspectImageView>(this, R.id.preview)
            busyIndicator = ButterKnife.findById<View>(this, R.id.busy_indicator)
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
            onViewListener!!.onCompleted()
            thumbnail.onCompleted()
        }

        if (hasPreviewView() && ThumbyService.isEligibleForPreview(mediaUri)) {
            RxView.attachEvents(this).limit(1).subscribe {

                // test if we need to load the thumby preview.
                if (hasPreviewView()) {
                    val uri = ThumbyService.thumbUri(mediaUri)

                    RxPicasso.load(picasso, picasso.load(uri).noPlaceholder())
                            .onErrorResumeNext(Observable.empty<Bitmap>())
                            .observeOn(AndroidSchedulers.mainThread())
                            .compose(RxLifecycleAndroid.bindView<Bitmap>(this))
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
            preloadHint.setTextColor(ContextCompat.getColor(context, ThemeHelper.accentColor()))
            addView(preloadHint)
        }
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
     * Implement to do dependency injection.
     */
    protected abstract fun injectComponent(component: ActivityComponent)

    /**
     * Sets the pixels image for this media view. You need to provide a width and height.
     * Those values will be used to place the pixels image correctly.
     */
    private fun updatePreview(info: PreviewInfo) {
        if (!hasPreviewView())
            return

        if (info.width > 0 && info.height > 0) {
            val aspect = info.width.toFloat() / info.height.toFloat()
            viewAspect = aspect
        }

        info.preview?.let { preview ->
            setPreviewDrawable(preview)

            if (preview is BitmapDrawable) {
                val bitmap = preview.bitmap
                thumbnail.onNext(bitmap)
            }
        }

        // we always have a thumbnail we can try to load.
        val aspect = info.width / info.height.toFloat()
        Observable.fromCallable<Bitmap> { fancyThumbnailGenerator.fancyThumbnail(info.previewUri, aspect) }
                .doOnError { err -> logger.warn("Could not generate fancy thumbnail", err) }
                .onErrorResumeNext(Observable.empty<Bitmap>())
                .compose(backgroundBindView<Bitmap>())
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
        AndroidUtility.removeView(previewView)
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
        AndroidUtility.removeView(busyIndicator)
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
            val gravity = params.gravity
            if (hasPreviewView()) {
                assert(previewView != null)
                (previewView!!.layoutParams as FrameLayout.LayoutParams).gravity = gravity
            }
        }
    }

    fun setPreviewDrawable(previewDrawable: Drawable) {
        if (hasPreviewView()) {
            this.previewView!!.setImageDrawable(previewDrawable)
        }
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

    private class PreviewTarget internal constructor(mediaView: MediaView) : Action1<Bitmap> {
        private val mediaView = WeakReference(mediaView)

        override fun call(bitmap: Bitmap) {
            this.mediaView.get()?.let { mediaView ->
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

        fun withAudio(audio: Boolean) = copy(audio = audio)

        fun withPreviewInfo(p: PreviewInfo) = copy(previewInfo = previewInfo)

        companion object {
            @JvmStatic
            fun of(activity: Activity, uri: MediaUri): Config {
                return Config(activity, uri)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("MediaView")

        private val MIN_PREVIEW_ASPECT = 1 / 30.0f

        const internal val ANIMATION_DURATION = 500

        private val DEFAULT_PARAMS = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL)
    }
}
