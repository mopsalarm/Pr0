package com.pr0gramm.app.ui.views.viewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.proxy.ProxyService;
import com.pr0gramm.app.ui.fragments.PostFragment;
import com.pr0gramm.app.ui.views.AspectImageView;
import com.pr0gramm.app.util.AndroidUtility;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;

import javax.annotation.Nullable;
import javax.inject.Inject;

import roboguice.RoboGuice;
import roboguice.inject.RoboInjector;
import rx.Observable;

import static android.view.GestureDetector.SimpleOnGestureListener;

/**
 */
public abstract class MediaView extends FrameLayout {
    protected final Logger logger = LoggerFactory.getLogger(getClass().getName()
            + " " + Integer.toHexString(System.identityHashCode(this)));

    private final GestureDetector gestureDetector;
    private boolean mediaShown;

    protected final Binder binder;
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
    private Settings settings;

    @Inject
    private Picasso picasso;

    @Inject
    private ProxyService proxyService;

    private float viewAspect = -1;

    protected MediaView(Context context, Binder binder, @LayoutRes Integer layoutId, MediaUri mediaUri,
                        Runnable onViewListener) {

        super(context);
        this.binder = binder;
        this.mediaUri = mediaUri;
        this.onViewListener = onViewListener;

        setLayoutParams(DEFAULT_PARAMS);
        if (layoutId != null) {
            LayoutInflater.from(context).inflate(layoutId, this);

            progress = findViewById(R.id.progress);
            preview = (AspectImageView) findViewById(R.id.preview);
            previewTarget = new PreviewTarget(this);
        } else {
            preview = null;
        }

        RoboInjector injector = RoboGuice.getInjector(context);
        injector.injectMembersWithoutViews(this);
        injector.injectViewMembers(this);

        // register the detector to handle double taps
        gestureDetector = new GestureDetector(context, gestureListener);

        showBusyIndicator();

        if (BuildConfig.DEBUG && mediaUri.isLocal()) {
            TextView preloadHint = new TextView(getContext());
            preloadHint.setText("preloaded");
            preloadHint.setLayoutParams(DEFAULT_PARAMS);
            preloadHint.setTextColor(getResources().getColor(R.color.primary));
            addView(preloadHint);
        }
    }

    /**
     * Sets the preview image for this media view. You need to provide a width and height.
     * Those values will be used to place the preview image correctly.
     */
    public void setPreviewImage(PostFragment.PreviewInfo info, String transitionName) {
        if (preview != null) {
            if (info.getWidth() > 0 && info.getHeight() > 0) {
                float aspect = (float) info.getWidth() / (float) info.getHeight();

                // clamp while loading the preview.
                aspect = Math.max(aspect, 1 / 3.0f);

                preview.setAspect(aspect);
                setViewAspect(aspect);
            }

            if (info.getPreview() != null) {
                preview.setImageDrawable(info.getPreview());

            } else if (info.getPreviewUri() != null) {
                if(getMediaUri().isLocal()) {
                    picasso.load(info.getPreviewUri())
                            .networkPolicy(NetworkPolicy.OFFLINE)
                            .into(previewTarget);
                } else {
                    // quickly load the preview into this view
                    picasso.load(info.getPreviewUri()).into(previewTarget);
                }

            } else {
                // no preview for this item, remove the view
                removePreviewImage();
            }

            ViewCompat.setTransitionName(preview, transitionName);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (viewAspect <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        } else {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = (int) (width / viewAspect) + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(width, height);

            measureChildren(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
    }

    /**
     * Removes the preview drawable.
     */
    public void removePreviewImage() {
        if (this.preview != null) {
            // cancel loading of preview, if there is still a request pending.
            picasso.cancelRequest(preview);

            AndroidUtility.removeView(preview);
            this.preview = null;
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    /**
     * This method must be called after a shared element
     * transition for the preview image ends.
     */
    public void onTransitionEnds() {
        if (preview != null && previewTarget != null) {

            if (isEligibleForThumbyPreview(mediaUri)) {
                // normalize url before fetching generating thumbnail
                String url = mediaUri.getBaseUri().toString()
                        .replace("https://", "http://")
                        .replace(".mpg", ".webm");

                String encoded = BaseEncoding.base64Url().encode(url.getBytes(Charsets.UTF_8));

                Uri image = Uri.parse("http://pr0.wibbly-wobbly.de/api/thumby/v1/" + encoded + "/thumb.jpg");
                picasso.load(image).noPlaceholder().into(previewTarget);
            }
        }
    }

    /**
     * Return true, if the thumby service can produce a preview for this url.
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
    private final SimpleOnGestureListener gestureListener = new GestureDetector.SimpleOnGestureListener() {
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

    public void rewind() {
        // do nothing by default
    }

    public static class Binder {
        private final Observable.Transformer transformer;

        public Binder(Observable.Transformer transformer) {
            this.transformer = transformer;
        }

        <T> Observable.Transformer<T, T> get() {
            //noinspection unchecked
            return transformer;
        }
    }

    public interface TapListener {
        boolean onSingleTap();

        boolean onDoubleTap();
    }

    public static class TapListenerAdapter implements TapListener {
        @Override
        public boolean onSingleTap() {
            return false;
        }

        @Override
        public boolean onDoubleTap() {
            return false;
        }

    }


    /**
     * Puts the loaded image into the preview container, if there
     * still is a preview container.
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
            ViewGroup.LayoutParams.WRAP_CONTENT);

}
