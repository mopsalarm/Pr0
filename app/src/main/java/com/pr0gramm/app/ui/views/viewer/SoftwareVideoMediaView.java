package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.widget.ImageView;

import com.google.inject.Inject;
import com.pr0gramm.app.Pr0grammApplication;
import com.pr0gramm.app.R;
import com.pr0gramm.app.mpeg.PictureBuffer;
import com.pr0gramm.app.mpeg.VideoConsumer;
import com.pr0gramm.app.mpeg.VideoDecoder;
import com.pr0gramm.app.vpx.WebmMediaPlayer;
import com.squareup.picasso.Downloader;

import java.io.InputStream;

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

    private static class MpegSoftwareMediaPlayer extends SoftwareMediaPlayer implements VideoConsumer {
        private PictureBuffer buffer;

        public MpegSoftwareMediaPlayer(InputStream inputStream) {
            super(inputStream);
        }

        @Override
        protected void playOnce(InputStream stream) throws Exception {
            logger.info("creating video decoder instance");
            VideoDecoder decoder = new VideoDecoder(this, stream);

            logger.info("start decoding mpeg file now");
            decoder.decodeSequence();

            logger.info("decoding mepg stream finished");
        }

        @Override
        public void sequenceStarted() {
            logger.info("sequence started");
        }

        @Override
        public void sequenceEnded() {
            logger.info("sequence ended");
        }

        @Override
        public void pictureDecoded(PictureBuffer picture) {
            Bitmap bitmap = requestBitmap(picture.width, picture.height);
            try {
                // post information about the newly received size info
                reportSize(picture.width, picture.height);

                bitmap.setPixels(picture.pixels, 0, picture.codedWidth, 0, 0,
                        picture.width, picture.height);
            } catch (Exception error) {
                returnBitmap(bitmap);
                throw error;
            }

            publishBitmap(bitmap);
        }

        @Override
        public PictureBuffer fetchBuffer(VideoDecoder decoder) throws InterruptedException {
            ensureStillRunning();

            // set the current delay
            publishFrameDelay((long) (1000 / decoder.getPictureRate()));

            // do nothing while paused.
            blockWhilePaused();

            if (buffer == null) {
                int width = decoder.getWidth();
                int height = decoder.getHeight();
                int codedWidth = decoder.getCodedWidth();
                int codedHeight = decoder.getCodedHeight();

                // logger.info("requesting buffer at {}x{} ({}x{})", width, height, codedWidth, codedHeight);
                buffer = new PictureBuffer(width, height, codedWidth, codedHeight);
            }

            return buffer;
        }
    }
}
