package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.View;
import android.widget.ImageView;

import com.google.common.base.Optional;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.ui.views.BusyIndicator;
import com.pr0gramm.app.util.BackgroundScheduler;
import com.squareup.picasso.Downloader;

import javax.inject.Inject;

import butterknife.Bind;
import pl.droidsonroids.gif.GifDrawable;
import rx.Observable;
import rx.Subscription;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.util.AndroidUtility.checkMainThread;

/**
 */
@SuppressLint("ViewConstructor")
public class GifMediaView extends AbstractProgressMediaView {
    @Inject
    Downloader downloader;

    @Inject
    Settings settings;

    @Bind(R.id.image)
    ImageView imageView;

    // the gif that is shown
    private GifDrawable gif;

    private Subscription dlGifSubscription;

    public GifMediaView(Activity context, MediaUri url, Runnable onViewListener) {
        super(context, R.layout.player_gif, url, onViewListener);
        imageView.setVisibility(INVISIBLE);

        loadGif();
    }

    private void loadGif() {
        Observable<GifLoader.DownloadStatus> loader = GifLoader
                .loader(downloader, getContext().getCacheDir(), getEffectiveUri())
                .subscribeOn(BackgroundScheduler.instance());

        dlGifSubscription = loader.compose(backgroundBindView()).subscribe(state -> {
            onDownloadProgress(state.getProgress());

            if (state.isFinished()) {
                hideBusyIndicator();

                gif = state.getDrawable();
                imageView.setImageDrawable(this.gif);
                setViewAspect((float) gif.getIntrinsicWidth() / gif.getIntrinsicHeight());

                if (isPlaying()) {
                    onMediaShown();
                } else {
                    gif.stop();
                }
            }
        }, defaultOnError());
    }

    private void onDownloadProgress(float progress) {
        checkMainThread();

        // logger.info("Download at " + ((int) (100 * progress)) + " percent.");

        View progressView = getProgressView();
        if (progressView instanceof BusyIndicator) {
            BusyIndicator bar = (BusyIndicator) progressView;
            bar.setProgress(progress);
        }
    }

    @Override
    public void onTransitionEnds() {
        super.onTransitionEnds();
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
        if (gif != null && isPlaying())
            gif.start();
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

    @Override
    public void onDestroy() {
        // unsubscribe and cancel downloader
        if (dlGifSubscription != null)
            dlGifSubscription.unsubscribe();

        imageView.setImageDrawable(null);

        if (gif != null)
            gif.recycle();

        super.onDestroy();
    }
}
