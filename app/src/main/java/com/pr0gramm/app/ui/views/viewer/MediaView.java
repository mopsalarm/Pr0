package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.jakewharton.rxbinding.view.RxView;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.InMemoryCacheService;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.ui.FancyExifThumbnailGenerator;
import com.pr0gramm.app.ui.PreviewInfo;
import com.pr0gramm.app.ui.views.AspectImageView;
import com.pr0gramm.app.util.AndroidUtility;
import com.pr0gramm.app.util.BackgroundScheduler;
import com.pr0gramm.app.util.RxPicasso;
import com.squareup.picasso.Picasso;
import com.trello.rxlifecycle.android.RxLifecycleAndroid;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;

import javax.annotation.Nullable;
import javax.inject.Inject;

import butterknife.ButterKnife;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.subjects.BehaviorSubject;
import rx.subjects.ReplaySubject;

import static android.view.GestureDetector.SimpleOnGestureListener;

/**
 */
public abstract class MediaView extends FrameLayout {
    private static final Logger logger = LoggerFactory.getLogger("MediaView");

    private static final float MIN_PREVIEW_ASPECT = 1 / 30.0f;
    static final int ANIMATION_DURATION = 500;

    private final PreviewTarget previewTarget = new PreviewTarget(this);
    private final BehaviorSubject<Void> onViewListener = BehaviorSubject.create();
    private final BehaviorSubject<Bitmap> thumbnail = BehaviorSubject.create();
    private final GestureDetector gestureDetector;
    private final MediaUri mediaUri;

    protected final Config config;

    private boolean mediaShown;
    private TapListener tapListener;
    private boolean resumed;
    private boolean playing;

    private float viewAspect = -1;

    private final ReplaySubject<View> controllerView = ReplaySubject.create();

    @Nullable
    private Rect clipBounds;

    @Nullable
    private View busyIndicator;

    @Nullable
    AspectImageView preview;

    @Inject
    Picasso picasso;

    @Inject
    InMemoryCacheService inMemoryCacheService;

    @Inject
    FancyExifThumbnailGenerator fancyThumbnailGenerator;

    MediaView(Config config, @LayoutRes Integer layoutId) {
        super(config.activity());

        this.config = config;

        // inject all the stuff!
        injectComponent(Dagger.activityComponent(config.activity()));

        this.mediaUri = config.mediaUri();

        setLayoutParams(DEFAULT_PARAMS);
        if (layoutId != null) {
            LayoutInflater.from(config.activity()).inflate(layoutId, this);
            ButterKnife.bind(this);

            preview = ButterKnife.findById(this, R.id.preview);
            busyIndicator = ButterKnife.findById(this, R.id.busy_indicator);
        }

        // register the detector to handle double taps
        gestureDetector = new GestureDetector(config.activity(), gestureListener);

        showPreloadedIndicator();

        RxView.detaches(this).subscribe(event -> {
            if (playing)
                stopMedia();

            if (resumed)
                onPause();

            // no more views will come now.
            controllerView.onCompleted();
            onViewListener.onCompleted();
            thumbnail.onCompleted();
        });

        if (hasPreviewView() && ThumbyService.isEligibleForPreview(mediaUri)) {
            RxView.attachEvents(this).limit(1).subscribe(event -> {

                // test if we need to load the thumby preview.
                if (hasPreviewView()) {
                    Uri uri = ThumbyService.thumbUri(mediaUri);

                    RxPicasso.load(picasso, picasso.load(uri).noPlaceholder())
                            .onErrorResumeNext(Observable.empty())
                            .observeOn(AndroidSchedulers.mainThread())
                            .compose(RxLifecycleAndroid.bindView(this))
                            .subscribe(previewTarget);
                }
            });
        }

        // set preview info
        PreviewInfo previewInfo = config.previewInfo().orNull();
        if (previewInfo != null)
            updatePreview(previewInfo);
    }

    /**
     * An observable that produces a value on the main thread if the video was seen.
     */
    public Observable<Void> viewed() {
        return onViewListener;
    }

    @SuppressLint("SetTextI18n")
    private void showPreloadedIndicator() {
        if (BuildConfig.DEBUG && mediaUri.isLocal()) {
            TextView preloadHint = new TextView(getContext());
            preloadHint.setText("preloaded");
            preloadHint.setLayoutParams(DEFAULT_PARAMS);
            preloadHint.setTextColor(ContextCompat.getColor(getContext(), ThemeHelper.accentColor()));
            addView(preloadHint);
        }
    }

    protected <T> Observable.Transformer<T, T> backgroundBindView() {
        return observable -> observable
                .subscribeOn(BackgroundScheduler.instance())
                .unsubscribeOn(BackgroundScheduler.instance())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(RxLifecycleAndroid.<T>bindView(this));
    }

    /**
     * Implement to do dependency injection.
     */
    protected abstract void injectComponent(ActivityComponent component);

    /**
     * Sets the pixels image for this media view. You need to provide a width and height.
     * Those values will be used to place the pixels image correctly.
     */
    private void updatePreview(PreviewInfo info) {
        if (!hasPreviewView())
            return;

        assert preview != null;
        if (info.getWidth() > 0 && info.getHeight() > 0) {
            float aspect = (float) info.getWidth() / (float) info.getHeight();
            setViewAspect(aspect);
        }

        boolean hasThumbnail = false;

        Drawable preview = info.getPreview();
        if (preview != null) {
            setPreviewDrawable(preview);
            hasThumbnail = true;

            if (preview instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) preview).getBitmap();
                thumbnail.onNext(bitmap);
            }
        }

        if (info.getPreviewUri() != null) {
            float aspect = info.getWidth() / (float) info.getHeight();
            Observable.fromCallable(() -> fancyThumbnailGenerator.fancyThumbnail(info.getPreviewUri(), aspect))
                    .doOnError(err -> logger.warn("Could not generate fancy thumbnail", err))
                    .onErrorResumeNext(Observable.empty())
                    .compose(backgroundBindView())
                    .subscribe(previewTarget);

            hasThumbnail = true;
        }

        if (!hasThumbnail) {
            // We have no preview image or this image, so we can remove the view entirely
            removePreviewImage();
        }
    }

    private boolean hasPreviewView() {
        return preview != null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (viewAspect <= 0) {
            // If we dont have a view aspect, we will let the content decide
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        } else {
            boolean heightUnspecified = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED;

            // we shouldnt get larger than this.
            int maxWidth = MeasureSpec.getSize(widthMeasureSpec);
            int maxHeight = MeasureSpec.getSize(heightMeasureSpec);

            int width;
            int height;
            if (heightUnspecified || maxWidth / (double) maxHeight < viewAspect) {
                width = maxWidth;
                height = (int) (maxWidth / viewAspect) + getPaddingTop() + getPaddingBottom();
            } else {
                width = (int) (maxHeight * viewAspect);
                height = maxHeight;
            }

            // use the calculated sizes!
            setMeasuredDimension(width, height);
            measureChildren(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
    }

    /**
     * Removes the preview image from this view.
     * This will not remove the view until the transition ended.
     */
    public void removePreviewImage() {
        AndroidUtility.removeView(preview);
        preview = null;

        onPreviewRemoved();
    }

    protected void onPreviewRemoved() {
        // implement action that should be executed
        // the moment the preview is removed
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
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
        public boolean onSingleTapConfirmed(MotionEvent event) {
            return onSingleTap(event);
        }
    };

    protected boolean onDoubleTap() {
        return tapListener != null && tapListener.onDoubleTap();

    }

    protected boolean onSingleTap(MotionEvent event) {
        return tapListener != null && tapListener.onSingleTap(event);
    }

    /**
     * Displays an indicator that something is loading. You need to pair
     * this with a call to {@link #hideBusyIndicator()}
     */
    protected void showBusyIndicator() {
        if (busyIndicator != null) {
            if (busyIndicator.getParent() == null)
                addView(busyIndicator);

            busyIndicator.setVisibility(View.VISIBLE);
        }
    }

    protected void showBusyIndicator(boolean show) {
        if (show) {
            showBusyIndicator();
        } else {
            hideBusyIndicator();
        }
    }

    /**
     * Hides the busy indicator that was shown in {@link #showBusyIndicator()}.
     */
    protected void hideBusyIndicator() {
        AndroidUtility.removeView(busyIndicator);
    }

    @Nullable
    public View getProgressView() {
        return busyIndicator;
    }

    @Nullable
    public AspectImageView getPreviewView() {
        return preview;
    }

    public boolean isResumed() {
        return resumed;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void onPause() {
        resumed = false;
    }

    public void onResume() {
        resumed = true;
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
        if (hasPreviewView()) {
            assert preview != null;
            preview.setAspect(viewAspect > 0
                    ? Math.max(viewAspect, MIN_PREVIEW_ASPECT)
                    : -1);
        }

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
    protected Uri getEffectiveUri() {
        return mediaUri.getBaseUri();
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
            if (!onViewListener.hasCompleted()) {
                onViewListener.onNext(null);
                onViewListener.onCompleted();
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        drawWithClipBounds(canvas, c -> super.dispatchDraw(canvas));
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void draw(Canvas canvas) {
        drawWithClipBounds(canvas, super::draw);
    }

    private void drawWithClipBounds(Canvas canvas, Action1<Canvas> action) {
        if (clipBounds != null) {
            canvas.save();

            // clip and draw!
            canvas.clipRect(clipBounds);
            action.call(canvas);

            canvas.restore();

        } else {
            action.call(canvas);
        }
    }

    public void setClipBoundsCompat(Rect clipBounds) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setClipBounds(clipBounds);
        } else if (this.clipBounds != clipBounds) {
            this.clipBounds = clipBounds;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
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

        // forward the gravity to the preview if possible
        if (params instanceof FrameLayout.LayoutParams) {
            int gravity = ((LayoutParams) params).gravity;
            if (hasPreviewView()) {
                assert preview != null;
                ((LayoutParams) preview.getLayoutParams()).gravity = gravity;
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    public void setPreviewDrawable(Drawable previewDrawable) {
        if (hasPreviewView()) {
            this.preview.setImageDrawable(previewDrawable);
        }
    }

    protected boolean hasAudio() {
        return config.audio();
    }

    protected void publishControllerView(View view) {
        controllerView.onNext(view);
    }

    public Observable<View> controllerView() {
        return controllerView;
    }

    public Observable<Bitmap> thumbnail() {
        return thumbnail;
    }

    public interface TapListener {
        boolean onSingleTap(MotionEvent event);

        boolean onDoubleTap();
    }

    private static class PreviewTarget implements Action1<Bitmap> {
        private final WeakReference<MediaView> mediaView;

        PreviewTarget(MediaView mediaView) {
            this.mediaView = new WeakReference<>(mediaView);
        }

        @Override
        public void call(Bitmap bitmap) {
            MediaView mediaView = this.mediaView.get();
            if (mediaView != null && mediaView.preview != null) {
                Drawable nextImage = new BitmapDrawable(mediaView.getResources(), bitmap);
                mediaView.setPreviewDrawable(nextImage);
            }

            mediaView.thumbnail.onNext(bitmap);
        }
    }

    private static final LayoutParams DEFAULT_PARAMS = new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL);

    @Value.Immutable
    public static abstract class Config {
        @Value.Parameter
        public abstract Activity activity();

        @Value.Parameter(order = 1)
        public abstract MediaUri mediaUri();

        public abstract Optional<PreviewInfo> previewInfo();

        @Value.Default
        public boolean audio() {
            return false;
        }
    }
}
