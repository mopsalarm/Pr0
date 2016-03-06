package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.View;
import android.widget.ImageView;

import com.google.common.base.Optional;
import com.jakewharton.rxbinding.view.RxView;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.GifDrawableLoader;
import com.pr0gramm.app.ui.views.BusyIndicator;

import javax.inject.Inject;

import butterknife.Bind;
import pl.droidsonroids.gif.GifDrawable;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.util.AndroidUtility.checkMainThread;
import static com.pr0gramm.app.util.AndroidUtility.endAction;

/**
 */
@SuppressLint("ViewConstructor")
public class GifMediaView extends AbstractProgressMediaView {
    @Inject
    Settings settings;

    @Inject
    GifDrawableLoader gifDrawableLoader;

    @Bind(R.id.image)
    ImageView imageView;

    // the gif that is shown
    private GifDrawable gif;

    public GifMediaView(Activity context, MediaUri url, Runnable onViewListener) {
        super(context, R.layout.player_gif, url.withProxy(true), onViewListener);
        imageView.setAlpha(0.f);
        loadGif();

        // cleanup on detach!
        RxView.detaches(this).subscribe(event -> {
            imageView.setImageDrawable(null);

            if (gif != null) {
                gif.recycle();
                gif = null;
            }
        });
    }

    private void loadGif() {
        showBusyIndicator();

        gifDrawableLoader.load(getEffectiveUri())
                .compose(backgroundBindView())
                .doAfterTerminate(this::hideBusyIndicator)
                .subscribe(this::onDownloadStatus, defaultOnError());
    }

    private void onDownloadStatus(GifDrawableLoader.DownloadStatus state) {
        checkMainThread();

        onDownloadProgress(state.progress);

        if (state.finished()) {
            gif = state.drawable;
            imageView.setImageDrawable(this.gif);

            setViewAspect((float) gif.getIntrinsicWidth() / gif.getIntrinsicHeight());

            if (isPlaying()) {
                imageView.animate().alpha(1.f)
                        .setListener(endAction(this::onMediaShown))
                        .setDuration(ANIMATION_DURATION)
                        .start();
            } else {
                imageView.setAlpha(1.f);
                gif.stop();
            }
        }
    }

    private void onDownloadProgress(float progress) {
        checkMainThread();

        View progressView = getProgressView();
        if (progressView instanceof BusyIndicator) {
            BusyIndicator bar = (BusyIndicator) progressView;
            bar.setProgress(progress);
        }
    }

    @Override
    protected void onPreviewRemoved() {
        imageView.setVisibility(VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (gif != null && isPlaying()) {
            gif.start();
            onMediaShown();
        }
    }

    @Override
    protected Optional<Float> getVideoProgress() {
        if (gif != null && isPlaying()) {
            int position = gif.getCurrentFrameIndex();
            int duration = gif.getNumberOfFrames();

            if (position >= 0 && duration > 0) {
                return Optional.of(position / (float) duration);
            }
        }

        return Optional.absent();
    }

    @Override
    protected void injectComponent(ActivityComponent component) {
        component.inject(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (gif != null && isPlaying())
            gif.pause();
    }

    @Override
    public void playMedia() {
        super.playMedia();
        if (gif != null && isPlaying()) {
            gif.start();
            onMediaShown();
        }
    }

    @Override
    public void stopMedia() {
        super.stopMedia();
        if (gif != null)
            gif.stop();
    }

    @Override
    public void rewind() {
        if (gif != null && isPlaying()) {
            gif.seekTo(0);
        }
    }
}
