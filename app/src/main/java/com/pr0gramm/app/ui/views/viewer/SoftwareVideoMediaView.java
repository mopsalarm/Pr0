package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;
import android.widget.ImageView;

import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.mpeg.MpegSoftwareMediaPlayer;
import com.pr0gramm.app.util.BackgroundScheduler;
import com.pr0gramm.app.vpx.WebmMediaPlayer;
import com.squareup.picasso.Downloader;
import com.trello.rxlifecycle.RxLifecycle;

import java.io.FileInputStream;
import java.io.InputStream;

import javax.inject.Inject;

import butterknife.Bind;
import rx.Observable;
import rx.Subscription;
import rx.functions.Func0;
import rx.util.async.Async;

import static com.google.common.base.Preconditions.checkState;
import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.util.AndroidUtility.toFile;
import static com.squareup.picasso.Downloader.Response;

/**
 */
@SuppressLint("ViewConstructor")
public class SoftwareVideoMediaView extends MediaView {
    @Bind(R.id.image)
    ImageView imageView;

    @Inject
    Downloader downloader;

    private SoftwareMediaPlayer videoPlayer;
    private Subscription loading;

    public SoftwareVideoMediaView(Activity context, MediaUri url, Runnable onViewListener) {
        super(context, R.layout.player_image, url, onViewListener);
    }

    private void asyncLoadVideo() {
        if (loading != null || videoPlayer != null) {
            return;
        }

        showBusyIndicator();

        loading = newVideoPlayer().compose(bindView()).finallyDo(() -> loading = null).subscribe(player -> {
            hideBusyIndicator();

            imageView.setImageDrawable(player.drawable());

            videoPlayer = player;
            videoPlayer.videoSize()
                    .compose(bindView())
                    .subscribe(this::onSizeChanged);

            videoPlayer.errors()
                    .compose(bindView())
                    .subscribe(defaultOnError());

            videoPlayer.buffering()
                    .compose(bindView())
                    .subscribe(buffering -> {
                        logger.info("Setting buffering to {}", buffering);
                        if (buffering) {
                            showBusyIndicator();
                        } else {
                            hideBusyIndicator();
                        }
                    });

            if (isPlaying()) {
                play();
            }
        }, defaultOnError());
    }

    /**
     * Creates a new video player for the current url asynchronously and returns
     * an observable that produces the player when it is initialize.
     */
    private Observable<SoftwareMediaPlayer> newVideoPlayer() {
        return Async.fromCallable(() -> {
            Uri uri = getEffectiveUri();

            // open the uri.
            InputStream inputStream;
            if ("file".equals(uri.getScheme())) {
                inputStream = new FileInputStream(toFile(uri));
            } else {
                Response response = downloader.load(uri, 0);
                inputStream = response.getInputStream();
            }

            String urlString = getMediaUri().toString();
            if (urlString.endsWith(".mpg") || urlString.endsWith(".mpeg"))
                return new MpegSoftwareMediaPlayer(getContext(), inputStream);

            if (urlString.endsWith(".webm"))
                return new WebmMediaPlayer(getContext(), inputStream);

            throw new RuntimeException("Unknown video type");
        }, BackgroundScheduler.instance());
    }


    private void onSizeChanged(SoftwareMediaPlayer.Size size) {
        setViewAspect(size.getAspectRatio());
    }

    private void loadAndPlay() {
        if (videoPlayer == null && loading == null) {
            asyncLoadVideo();

        } else if (videoPlayer != null) {
            play();
        }
    }

    private void play() {
        checkState(videoPlayer != null, "Can not start video player which is null.");

        videoPlayer.start();
        videoPlayer.drawable()
                .frameAvailable()
                .take(1)
                .compose(RxLifecycle.<Void>bindView(this))
                .subscribe(ignored -> onMediaShown());
    }

    private void stopAndDestroy() {
        if (loading != null) {
            loading.unsubscribe();
            loading = null;
        }

        if (videoPlayer != null) {
            videoPlayer.stop();

            Async.start(new DestroyAction(videoPlayer), BackgroundScheduler.instance());
            videoPlayer = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isPlaying()) {
            loadAndPlay();
        }
    }

    @Override
    protected void injectComponent(ActivityComponent component) {
        component.inject(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (videoPlayer != null && isPlaying())
            videoPlayer.pause();
    }

    @Override
    public void playMedia() {
        super.playMedia();
        if (isPlaying()) {
            loadAndPlay();
        }
    }

    @Override
    public void stopMedia() {
        super.stopMedia();
        stopAndDestroy();
    }

    @Override
    public void onDestroy() {
        imageView.setImageDrawable(null);
        stopAndDestroy();
        super.onDestroy();
    }

    private static class DestroyAction implements Func0<Object> {
        private final SoftwareMediaPlayer player;

        public DestroyAction(SoftwareMediaPlayer player) {
            this.player = player;
        }

        @Override
        public Object call() {
            player.destroy();
            return null;
        }
    }
}
