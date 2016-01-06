package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;
import android.widget.ImageView;

import com.google.common.base.Optional;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.mpeg.MpegSoftwareMediaPlayer;
import com.pr0gramm.app.util.BackgroundScheduler;
import com.pr0gramm.app.vpx.WebmMediaPlayer;
import com.squareup.picasso.Downloader;
import com.trello.rxlifecycle.RxLifecycle;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.inject.Inject;

import butterknife.Bind;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func0;
import rx.subjects.BehaviorSubject;
import rx.util.async.Async;

import static com.google.common.base.Preconditions.checkState;
import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.util.AndroidUtility.checkMainThread;
import static com.pr0gramm.app.util.AndroidUtility.checkNotMainThread;
import static com.pr0gramm.app.util.AndroidUtility.toFile;

/**
 */
@SuppressLint("ViewConstructor")
public class SoftwareVideoMediaView extends AbstractProgressMediaView {
    @Bind(R.id.image)
    ImageView imageView;

    @Inject
    Downloader downloader;


    private Subscription loading;
    private SoftwareMediaPlayer videoPlayer;

    public SoftwareVideoMediaView(Activity context, MediaUri url, Runnable onViewListener) {
        // we always proxy because of caching and stuff
        super(context, R.layout.player_software_decoder, url.withProxy(true), onViewListener);
        imageView.setVisibility(INVISIBLE);
    }

    private void loadVideoAsync() {
        if (loading != null || videoPlayer != null) {
            return;
        }

        showBusyIndicator();

        loading = newVideoPlayer()
                .observeOn(AndroidSchedulers.mainThread())
                .compose(RxLifecycle.bindView(this))
                .finallyDo(() -> loading = null)
                .subscribe(this::onVideoPlayerAvailable, defaultOnError());
    }

    private void onVideoPlayerAvailable(SoftwareMediaPlayer player) {
        checkMainThread();

        imageView.setImageDrawable(player.drawable());

        videoPlayer = player;

        videoPlayer.videoSize()
                .compose(simpleBindView())
                .subscribe(this::onSizeChanged);

        videoPlayer.errors()
                .compose(simpleBindView())
                .subscribe(defaultOnError());

        videoPlayer.buffering()
                .compose(simpleBindView())
                .subscribe(buffering -> {
                    if (buffering) {
                        showBusyIndicator();
                    } else {
                        hideBusyIndicator();
                    }
                });

        if (isPlaying()) {
            play();
        }
    }

    /**
     * Creates a new video player for the current url asynchronously and returns
     * an observable that produces the player when it is initialize.
     */
    private Observable<SoftwareMediaPlayer> newVideoPlayer() {
        BehaviorSubject<SoftwareMediaPlayer> result = BehaviorSubject.create();

        BackgroundScheduler.instance().createWorker().schedule(() -> {
            try {
                // open the uri.
                InputStream inputStream = openMediaInputStream();

                String urlString = getMediaUri().toString();
                if (urlString.endsWith(".mpg") || urlString.endsWith(".mpeg")) {
                    result.onNext(new MpegSoftwareMediaPlayer(getContext(), inputStream));
                    result.onCompleted();
                    return;
                }

                if (urlString.endsWith(".webm")) {
                    result.onNext(new WebmMediaPlayer(getContext(), inputStream));
                    result.onCompleted();
                    return;
                }

                result.onError(new RuntimeException("invalid video format at " + urlString));

            } catch (IOException error) {
                result.onError(error);
            }
        });

        return result;
    }

    private InputStream openMediaInputStream() throws IOException {
        checkNotMainThread();

        Uri uri = getEffectiveUri();
        if ("file".equals(uri.getScheme())) {
            return new FileInputStream(toFile(uri));
        } else {
            return downloader.load(uri, 0).getInputStream();
        }
    }

    @Override
    protected Optional<Float> getVideoProgress() {
        SoftwareMediaPlayer player = this.videoPlayer;
        if (player != null && player.getDuration() > 0) {
            float progress = player.getCurrentPosition() / (float) player.getDuration();
            if (progress >= 0 && progress <= 1) {
                return Optional.of(progress);
            }
        }

        return Optional.absent();
    }

    private void onSizeChanged(SoftwareMediaPlayer.Size size) {
        setViewAspect(size.getAspectRatio());
        cacheMediaSize(size.getWidth(), size.getHeight());
    }

    private void loadAndPlay() {
        if (videoPlayer == null && loading == null) {
            loadVideoAsync();

        } else if (videoPlayer != null) {
            play();
        }
    }

    private void play() {
        checkState(videoPlayer != null, "Can not start video player which is null.");

        videoPlayer.drawable()
                .frameAvailable()
                .take(1)
                .compose(RxLifecycle.<Void>bindView(this))
                .subscribe(ignored -> {
                    checkMainThread();
                    onMediaShown();
                });

        videoPlayer.start();
    }

    @Override
    protected void onPreviewRemoved() {
        imageView.setVisibility(VISIBLE);
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

        DestroyAction(SoftwareMediaPlayer player) {
            this.player = player;
        }

        @Override
        public Object call() {
            player.destroy();
            return null;
        }
    }
}
