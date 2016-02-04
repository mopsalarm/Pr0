package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.View;

import com.google.common.base.Optional;
import com.jakewharton.rxbinding.view.RxView;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.GifDrawableLoader;
import com.pr0gramm.app.ui.views.BusyIndicator;

import javax.inject.Inject;

import butterknife.Bind;
import pl.droidsonroids.gif.GifTextureView;
import pl.droidsonroids.gif.InputSource;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.util.AndroidUtility.checkMainThread;

/**
 */
@SuppressLint("ViewConstructor")
public class GifMediaView extends AbstractProgressMediaView {
    @Inject
    Settings settings;

    @Inject
    GifDrawableLoader gifDrawableLoader;

    @Bind(R.id.image)
    GifTextureView gifTextureView;

    // the gif that is shown
    private InputSource inputSource;

    public GifMediaView(Activity context, MediaUri url, Runnable onViewListener) {
        super(context, R.layout.player_gif, url.withProxy(true), onViewListener);
        gifTextureView.setVisibility(INVISIBLE);
        loadGif();

        // cleanup on detach!
        RxView.detaches(this).subscribe(event -> {
            gifTextureView.setInputSource(null);
        });
    }

    private void loadGif() {
        showBusyIndicator();

        gifDrawableLoader.load(getEffectiveUri())
                .compose(backgroundBindView())
                .finallyDo(this::hideBusyIndicator)
                .subscribe(this::onDownloadStatus, defaultOnError());
    }

    private void onDownloadStatus(GifDrawableLoader.DownloadStatus state) {
        checkMainThread();

        onDownloadProgress(state.progress);

        if (state.finished()) {
            this.inputSource = state.source;
            gifTextureView.setInputSource(state.source);

            if (isPlaying()) {
                onMediaShown();
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
        gifTextureView.setVisibility(VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (inputSource != null && isPlaying()) {
            gifTextureView.setInputSource(inputSource);
            onMediaShown();
        }
    }

    @Override
    protected Optional<Float> getVideoProgress() {
        return Optional.absent();
    }

    @Override
    protected void injectComponent(ActivityComponent component) {
        component.inject(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        gifTextureView.setInputSource(null);
    }

    @Override
    public void playMedia() {
        super.playMedia();
        if (inputSource != null && isPlaying()) {
            gifTextureView.setInputSource(inputSource);
            onMediaShown();
        }
    }

    @Override
    public void stopMedia() {
        super.stopMedia();
        gifTextureView.setInputSource(null);
    }

    @Override
    public void rewind() {
        gifTextureView.setInputSource(null);
        if (isPlaying())
            gifTextureView.setInputSource(inputSource);
    }
}
