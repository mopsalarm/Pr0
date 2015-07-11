package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.widget.ImageView;

import com.google.inject.Inject;
import com.pr0gramm.app.Pr0grammApplication;
import com.pr0gramm.app.R;
import com.pr0gramm.app.mpeg.MpegSoftwareMediaPlayer;
import com.pr0gramm.app.vpx.WebmMediaPlayer;
import com.squareup.picasso.Downloader;

import roboguice.inject.InjectView;
import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;
import rx.util.async.Async;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;

/**
 */
@SuppressLint("ViewConstructor")
public class SoftwareVideoMediaView extends MediaView {
    @InjectView(R.id.image)
    private ImageView imageView;

    @Inject
    private Downloader downloader;

    private SoftwareMediaPlayer videoPlayer;
    private Subscription loading;

    public SoftwareVideoMediaView(Context context, Binder binder, MediaUri url, Runnable onViewListener) {
        super(context, binder, R.layout.player_image, url, onViewListener);
    }

    private void asyncLoadVideo() {
        if (loading != null || videoPlayer != null) {
            return;
        }

        loading = binder.bind(newVideoPlayer()).finallyDo(() -> loading = null).subscribe(mpeg -> {
            hideBusyIndicator();

            videoPlayer = mpeg;
            imageView.setImageDrawable(videoPlayer.drawable());
            binder.bind(videoPlayer.videoSize()).subscribe(this::onSizeChanged);

            if (isPlaying()) {
                videoPlayer.start();
                onMediaShown();
            }
        }, defaultOnError());
    }

    /**
     * Creates a new video player for the current url asynchronously and returns
     * an observable that produces the player when it is initialize.
     */
    private Observable<SoftwareMediaPlayer> newVideoPlayer() {
        return Async.fromCallable(() -> {
            Downloader.Response response = downloader.load(getEffectiveUri(), 0);

            String urlString = getMediaUri().toString();
            if (urlString.endsWith(".mpg") || urlString.endsWith(".mpeg"))
                return new MpegSoftwareMediaPlayer(response.getInputStream());

            if (urlString.endsWith(".webm"))
                return new WebmMediaPlayer(response.getInputStream());

            throw new RuntimeException("Unknown video type");
        }, Schedulers.io());
    }


    private void onSizeChanged(SoftwareMediaPlayer.Size size) {
        setViewAspect(size.getAspectRatio());
    }

    private void loadAndPlay() {
        if (videoPlayer == null && loading == null) {
            asyncLoadVideo();

        } else if (videoPlayer != null) {
            videoPlayer.start();
            onMediaShown();
        }
    }

    private void stopAndDestroy() {
        if (loading != null) {
            loading.unsubscribe();
            loading = null;
        }

        if (videoPlayer != null) {
            videoPlayer.stop();

            Pr0grammApplication.getRefWatcher().watch(videoPlayer);
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

}
