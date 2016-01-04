package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.akodiakson.sdk.simple.Sdk;
import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.LocalCacheService;
import com.pr0gramm.app.services.proxy.ProxyService;
import com.pr0gramm.app.ui.BackgroundBitmapDrawable;
import com.pr0gramm.app.ui.PreviewInfo;
import com.pr0gramm.app.ui.views.AspectImageView;
import com.pr0gramm.app.util.AndroidUtility;
import com.pr0gramm.app.util.BackgroundScheduler;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import com.trello.rxlifecycle.RxLifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;

import javax.annotation.Nullable;
import javax.inject.Inject;

import butterknife.ButterKnife;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;

import static android.view.GestureDetector.SimpleOnGestureListener;

/**
 */
public abstract class MediaView extends FrameLayout {
    private static final Logger logger = LoggerFactory.getLogger("MediaView");

    private final GestureDetector gestureDetector;
    private boolean mediaShown;

    protected final MediaUri mediaUri;

    private Runnable onViewListener;

    private TapListener tapListener;
    private boolean started;
    private boolean resumed;
    private boolean playing;

    private PreviewTarget previewTarget;

    @Nullable
    private View progress;

    @Nullable
    protected AspectImageView preview;

    @Inject
    Settings settings;

    @Inject
    Picasso picasso;

    @Inject
    LocalCacheService localCacheService;

    @Inject
    ProxyService proxyService;

    private float viewAspect = -1;
    private Rect clipBounds;
    private boolean transitionEnded;
    private boolean previewRemoved;

    @SuppressLint("SetTextI18n")
    protected MediaView(Activity activity, @LayoutRes Integer layoutId, MediaUri mediaUri,
                        Runnable onViewListener) {

        super(activity);
        this.mediaUri = mediaUri;
        this.onViewListener = onViewListener;

        setLayoutParams(DEFAULT_PARAMS);
        if (layoutId != null) {
            LayoutInflater.from(activity).inflate(layoutId, this);

            progress = findViewById(R.id.progress);
            preview = (AspectImageView) findViewById(R.id.preview);
            previewTarget = new PreviewTarget(this);
        } else {
            preview = null;
        }

        // inject all the stuff!
        injectComponent(Dagger.activityComponent(activity));
        ButterKnife.bind(this);

        // register the detector to handle double taps
        gestureDetector = new GestureDetector(activity, gestureListener);

        showBusyIndicator();

        if (BuildConfig.DEBUG && mediaUri.isLocal()) {
            TextView preloadHint = new TextView(getContext());
            preloadHint.setText("preloaded");
            preloadHint.setLayoutParams(DEFAULT_PARAMS);
            preloadHint.setTextColor(ContextCompat.getColor(getContext(), R.color.primary));
            addView(preloadHint);
        }

        updateBackgroundWithPadding(0, 0, 0, 0);
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        updateBackgroundWithPadding(left, top, right, bottom);
    }

    private void updateBackgroundWithPadding(int left, int top, int right, int bottom) {
        if (!previewRemoved) {
            Bitmap bitmap = localCacheService.lowQualityPreview(mediaUri.getId());
            Drawable bitmapDrawable = new BackgroundBitmapDrawable(new BitmapDrawable(getResources(), bitmap));
            Drawable insetDrawable = new InsetDrawable(bitmapDrawable, left, top, right, bottom);
            AndroidUtility.setViewBackground(this, insetDrawable);
        }
    }

    protected <T> Observable.Transformer<T, T> bindView() {
        return observable -> observable
                .subscribeOn(BackgroundScheduler.instance())
                .unsubscribeOn(BackgroundScheduler.instance())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(RxLifecycle.<T>bindView(this));
    }

    protected abstract void injectComponent(ActivityComponent component);

    /**
     * Sets the pixels image for this media view. You need to provide a width and height.
     * Those values will be used to place the pixels image correctly.
     */
    public void setPreviewImage(PreviewInfo info, String transitionName) {
        if (preview != null) {
            ViewCompat.setTransitionName(preview, transitionName);

            if (info.getWidth() > 0 && info.getHeight() > 0) {
                float aspect = (float) info.getWidth() / (float) info.getHeight();

                // clamp while loading the pixels.
                aspect = Math.max(aspect, 1 / 3.0f);

                preview.setAspect(aspect);
                setViewAspect(aspect);
            }

            if (info.getPreview() != null) {
                preview.setImageDrawable(info.getPreview());

            } else if (info.getPreviewUri() != null) {
                if (getMediaUri().isLocal()) {
                    picasso.load(info.getPreviewUri())
                            .networkPolicy(NetworkPolicy.OFFLINE)
                            .into(previewTarget);
                } else {
                    // quickly load the pixels into this view
                    picasso.load(info.getPreviewUri()).into(previewTarget);
                }

            } else {
                // no pixels for this item, remove the view
                removePreviewImage();
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (viewAspect <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        } else {
            Point size = measureToSize(widthMeasureSpec, heightMeasureSpec, viewAspect);

            int width = size.x, height = size.y;
            setMeasuredDimension(width, height);
            measureChildren(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
    }

    protected Point measureToSize(int widthMeasureSpec, int heightMeasureSpec, float aspect) {
        boolean heightUnspecified = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED;

        int maxHeight = MeasureSpec.getSize(heightMeasureSpec);
        int maxWidth = MeasureSpec.getSize(widthMeasureSpec);

        int width;
        int height;
        if (heightUnspecified || maxWidth / (double) maxHeight < aspect) {
            width = maxWidth;
            height = (int) (width / aspect) + getPaddingTop() + getPaddingBottom();

        } else {
            height = maxHeight;
            width = (int) (height * aspect);
        }

        return new Point(width, height);
    }

    public boolean hasTransitionEnded() {
        return transitionEnded;
    }

    /**
     * Removes the pixels drawable.
     */

    public void removePreviewImage() {
        previewRemoved = true;
        if (transitionEnded) {
            if (this.preview != null) {
                // cancel loading of pixels, if there is still a request pending.
                picasso.cancelRequest(preview);

                AndroidUtility.removeView(preview);
                this.preview = null;
            }

            // remove the background
            AndroidUtility.setViewBackground(this, null);
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    /**
     * This method must be called after a shared element
     * transition for the pixels image ends.
     */
    public void onTransitionEnds() {
        transitionEnded = true;

        if (previewRemoved)
            removePreviewImage();

        if (preview != null && previewTarget != null) {
            if (isEligibleForThumbyPreview(mediaUri)) {
                // normalize url before fetching generated thumbnail
                String url = mediaUri.getBaseUri().toString()
                        .replace("https://", "http://")
                        .replace(".mpg", ".webm");

                String encoded = BaseEncoding.base64Url().encode(url.getBytes(Charsets.UTF_8));

                Uri image = Uri.parse("https://pr0.wibbly-wobbly.de/api/thumby/v1/" + encoded + "/thumb.jpg");
                picasso.load(image).noPlaceholder().into(previewTarget);
            }
        }
    }

    /**
     * Return true, if the thumby service can produce a pixels for this url.
     * This is currently possible for gifs and videos.
     */
    private static boolean isEligibleForThumbyPreview(MediaUri url) {
        MediaUri.MediaType type = url.getMediaType();
        return type == MediaUri.MediaType.VIDEO || type == MediaUri.MediaType.GIF;
    }

    /**
     * The listener that handles double tapping
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final SimpleOnGestureListener gestureListener = new SimpleOnGestureListener() {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            return MediaView.this.onDoubleTap();
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            return onSingleTap();
        }
    };

    protected boolean onDoubleTap() {
        return tapListener != null && tapListener.onDoubleTap();

    }

    protected boolean onSingleTap() {
        return tapListener != null && tapListener.onSingleTap();
    }

    /**
     * Displays an indicator that something is loading. You need to pair
     * this with a call to {@link #hideBusyIndicator()}
     */
    protected void showBusyIndicator() {
        if (progress != null) {
            if (progress.getParent() == null)
                addView(progress);

            progress.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Hides the busy indicator that was shown in {@link #showBusyIndicator()}.
     */
    protected void hideBusyIndicator() {
        if (progress != null) {
            progress.setVisibility(View.GONE);

            ViewParent parent = progress.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(progress);
            }
        }
    }

    @Nullable
    public View getProgressView() {
        return progress;
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isResumed() {
        return resumed;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void onStart() {
        started = true;
    }

    public void onStop() {
        started = false;
    }

    public void onPause() {
        resumed = false;
    }

    public void onResume() {
        resumed = true;
    }

    public void onDestroy() {
        picasso.cancelRequest(previewTarget);

        if (playing)
            stopMedia();

        if (resumed)
            onPause();

        if (started)
            onStop();
    }

    public TapListener getTapListener() {
        return tapListener;
    }

    public void setTapListener(TapListener tapListener) {
        this.tapListener = tapListener;
    }

    /**
     * Sets the aspect ratio of this view. Will be ignored, if not positive and size
     * is then estimated from the children. If aspect is provided, the size of
     * the view is estimated from its parents width.
     */
    public void setViewAspect(float viewAspect) {
        if (this.viewAspect != viewAspect) {
            this.viewAspect = viewAspect;
            requestLayout();
        }
    }

    public float getViewAspect() {
        return viewAspect;
    }

    /**
     * Returns the url that this view should display.
     */
    protected MediaUri getMediaUri() {
        return mediaUri;
    }

    /**
     * Gets the effective uri that should be downloaded
     */
    public Uri getEffectiveUri() {
        if (!mediaUri.isLocal() && mediaUri.hasProxyFlag()) {
            return proxyService.proxy(mediaUri.getBaseUri());
        } else {
            return mediaUri.getBaseUri();
        }
    }

    public void playMedia() {
        logger.info("Should start playing media");
        playing = true;

        if (mediaShown) {
            onMediaShown();
        }
    }

    public void stopMedia() {
        logger.info("Should stop playing media");
        playing = false;
    }

    protected void onMediaShown() {
        removePreviewImage();
        mediaShown = true;

        if (isPlaying() && onViewListener != null) {
            onViewListener.run();
            onViewListener = null;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (clipBounds != null) {
            canvas.save();
            canvas.clipRect(clipBounds);
            super.dispatchDraw(canvas);
            canvas.restore();

        } else {
            super.dispatchDraw(canvas);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void setClipBoundsCompat(Rect clipBounds) {
        if (Sdk.isAtLeastLollipop()) {
            setClipBounds(clipBounds);
        } else if (this.clipBounds != clipBounds) {
            this.clipBounds = clipBounds;
            if (Sdk.isAtLeastJellyBeanMR2()) {
                setClipBounds(clipBounds);
            } else {
                invalidate();
            }
        }
    }

    public void rewind() {
        // do nothing by default
    }

    public MediaView getActualMediaView() {
        return this;
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        super.setLayoutParams(params);

        if (params instanceof FrameLayout.LayoutParams) {
            int gravity = ((LayoutParams) params).gravity;
            if (preview != null) {
                ((LayoutParams) preview.getLayoutParams()).gravity = gravity;
            }
        }

    }

    public interface TapListener {
        boolean onSingleTap();

        boolean onDoubleTap();
    }

    /**
     * Puts the loaded image into the pixels container, if there
     * still is a pixels container.
     */
    private static class PreviewTarget implements Target {
        private final WeakReference<MediaView> mediaView;

        public PreviewTarget(MediaView mediaView) {
            this.mediaView = new WeakReference<>(mediaView);
        }

        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
            MediaView mediaView = this.mediaView.get();
            if (mediaView != null && mediaView.preview != null) {
                mediaView.preview.setImageBitmap(bitmap);
            }
        }

        @Override
        public void onBitmapFailed(Drawable errorDrawable) {
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
        }
    }

    private static final LayoutParams DEFAULT_PARAMS = new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL);

}
