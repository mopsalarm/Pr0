package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.inject.Inject;
import com.pr0gramm.app.DialogBuilder;
import com.pr0gramm.app.LogcatUtility;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;

import java.io.File;
import java.io.IOException;

import roboguice.inject.InjectView;
import rx.functions.Actions;
import rx.util.async.Async;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.pr0gramm.app.AndroidUtility.checkMainThread;
import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;

/**
 * Plays videos in a not optimal but compatible way.
 */
@SuppressLint("ViewConstructor")
public class VideoMediaView extends MediaView implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnVideoSizeChangedListener, MediaPlayer.OnInfoListener {

    @InjectView(R.id.video)
    private TextureView surfaceView;

    @InjectView(R.id.video_container)
    private ViewGroup videoContainer;

    @Inject
    private Settings settings;

    private State targetState = State.IDLE;
    private State currentState = State.IDLE;
    private MediaPlayer mediaPlayer;

    private SurfaceTextureListenerImpl surfaceHolder;
    private boolean mediaPlayerHasTexture;

    private int retryCount;

    public VideoMediaView(Context context, Binder binder, String url) {
        super(context, binder, R.layout.player_video, url);

        logger.info("Playing webm " + url);

        surfaceHolder = new SurfaceTextureListenerImpl();
        surfaceView.setSurfaceTextureListener(surfaceHolder);

        moveTo_Idle();
    }

    @Override
    public void onResume() {
        super.onResume();
        moveTo(isPlaying() ? State.PLAYING : State.IDLE);
    }

    @Override
    public void onPause() {
        super.onPause();
        moveTo(State.IDLE);
    }

    @Override
    public void playMedia() {
        super.playMedia();
        moveTo(State.PLAYING);
    }

    @Override
    public void stopMedia() {
        super.stopMedia();
        moveTo(currentState == State.PLAYING ? State.PAUSED : State.IDLE);
    }

    private void moveTo(State target) {
        checkMainThread();

        targetState = target;
        if (currentState == targetState)
            return;

        try {
            logger.info("Moving from state " + currentState + " to " + targetState);
            switch (targetState) {
                case IDLE:
                    moveTo_Idle();
                    break;

                case PAUSED:
                    moveTo_Pause();
                    break;

                case PLAYING:
                    moveTo_Playing();
                    break;
            }
        } catch (Exception err) {
            // log this exception
            defaultOnError().call(err);
        }
    }

    private void moveTo_Playing() {
        if (currentState == State.PAUSED) {
            if (!mediaPlayerHasTexture && surfaceHolder.hasTexture())
                setMediaPlayerTexture(surfaceHolder.getTexture());

            if (mediaPlayerHasTexture) {
                mediaPlayer.start();
                currentState = State.PLAYING;
            }
        }

        if (currentState == State.IDLE) {
            openMediaPlayer();
        }
    }

    private void setMediaPlayerTexture(SurfaceTexture texture) {
        try {
            if (texture != null) {
                if (!mediaPlayerHasTexture && mediaPlayer != null) {
                    logger.info("Setting surface on MediaPlayer");
                    mediaPlayer.setSurface(new Surface(texture));
                    mediaPlayerHasTexture = true;
                }
            } else {
                if (mediaPlayer != null) {
                    logger.info("Removing surface from MediaPlayer");
                    mediaPlayer.setSurface(null);
                }

                mediaPlayerHasTexture = false;
            }
        } catch (Exception err) {
            defaultOnError().call(err);
        }
    }

    private void moveTo_Pause() {
        if (currentState == State.PLAYING) {
            mediaPlayer.pause();
            currentState = State.PAUSED;
        }

        if (currentState == State.IDLE) {
            openMediaPlayer();
        }
    }

    private void moveTo_Idle() {
        currentState = State.IDLE;

        if (mediaPlayer != null) {
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        setMediaPlayerTexture(null);

        if (surfaceHolder.hasTexture()) {
            logger.info("Detaching TextureView");
            videoContainer.removeView(surfaceView);
        }

        // destroy the previous texture
        surfaceHolder.destroyTexture();

        surfaceView.setAlpha(0);
        showBusyIndicator();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        logger.info("MediaPlayer is prepared");

        currentState = State.PAUSED;
        if (targetState == State.IDLE) {
            moveTo(State.IDLE);
            return;
        }

        mp.setLooping(true);
        mp.setVolume(0, 0);

        hideBusyIndicator();
        surfaceView.setAlpha(1);

        // if we already have a surface, move on
        if (mediaPlayerHasTexture)
            moveTo(targetState);
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        // scale view correctly
        float aspect = width / (float) height;
        resizeViewerView(surfaceView, aspect, 10);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        // release the player now
        moveTo(State.IDLE);

        final int METHOD_CALLED_IN_INVALID_STATE = -38;

        if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED
                || what == METHOD_CALLED_IN_INVALID_STATE
                || extra == METHOD_CALLED_IN_INVALID_STATE) {

            if (retryCount < 3) {
                retryCount++;

                // try again in a moment
                if (isPlaying()) {
                    postDelayed(() -> {
                        if (isPlaying()) {
                            moveTo(State.PLAYING);
                        }
                    }, 500);
                }

                return true;
            }
        }

        String message = String.format("Error playing this video (%d, %d)", what, extra);
        logger.info("Could not play video: " + message);

        try {
            if (what == MediaPlayer.MEDIA_ERROR_UNKNOWN && extra == MediaPlayer.MEDIA_ERROR_IO) {
                DialogBuilder.start(getContext())
                        .content(R.string.could_not_play_video_io)
                        .positive(R.string.okay)
                        .show();

                return true;
            }

            if (what == 262) {
                Optional<File> logFile = LogcatUtility.dump();
                if (logFile.isPresent()) {
                    DialogBuilder.start(getContext())
                            .content(getContext().getString(R.string.could_not_play_video_262, logFile.get()))
                            .positive(R.string.okay)
                            .show();

                    return true;
                }
            }

            DialogBuilder.start(getContext())
                    .content(R.string.could_not_play_video)
                    .positive(R.string.okay)
                    .show();

        } catch (Exception ignored) {
        }

        return true;
    }

    private void openMediaPlayer() {
        moveTo_Idle();
        currentState = State.PREPARING;

        // re-attach the view, if not yet there.
        if (surfaceView.getParent() == null) {
            logger.info("Attaching TextureView back");
            videoContainer.addView(surfaceView, 0);
        }

        logger.info("Creating new MediaPlayer");
        mediaPlayerHasTexture = false;
        mediaPlayer = new MediaPlayer();
        Async.fromCallable(() -> {
            try {
                mediaPlayer.setDataSource(getContext(), Uri.parse(url));
                mediaPlayer.setOnPreparedListener(this);
                mediaPlayer.setOnInfoListener(this);
                mediaPlayer.setOnErrorListener(this);
                mediaPlayer.setOnVideoSizeChangedListener(this);
                mediaPlayer.prepareAsync();
                return mediaPlayer;

            } catch (IOException error) {
                throw Throwables.propagate(error);
            }
        }).subscribe(Actions.empty(), error -> {
            moveTo(State.IDLE);
            defaultOnError().call(error);
        });
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        logger.info("MediaPlayer.info({}, {})", what, extra);
        return true;
    }

    private class SurfaceTextureListenerImpl implements TextureView.SurfaceTextureListener {
        private SurfaceTexture texture;

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            logger.info("Texture available at size " + width + "x" + height);
            if (this.texture == null) {
                logger.info("Keeping new Texture");

                this.texture = surface;
                if (mediaPlayer != null) {
                    setMediaPlayerTexture(texture);
                }

            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && !settings.dontRestoreSurfaceTexture()) {
                tryRestoreTexture(this.texture);

            } else if (mediaPlayer != null) {
                setMediaPlayerTexture(texture);
            }

            if (currentState.after(State.PREPARING) && isPlaying())
                moveTo(State.PLAYING);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            if (currentState.after(State.IDLE)) {
                // goto pause mode and retain the current surface
                moveTo(State.PAUSED);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && !settings.dontRestoreSurfaceTexture()) {
                    logger.info("Keeping Texture after onDestroyed-event");
                    return false;
                } else {
                    texture = null;

                    moveTo(State.PAUSED);
                    setMediaPlayerTexture(null);

                    logger.info("Destroying Texture in onDestroyed-event because of old android.");
                    return true;
                }

            } else {
                logger.info("Destroying Texture in onDestroyed-event");

                texture = null;
                return true;
            }
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }

        public boolean hasTexture() {
            return texture != null;
        }

        public SurfaceTexture getTexture() {
            return texture;
        }

        public void destroyTexture() {
            if (texture != null) {
                logger.info("Destroying Texture");

                texture.release();
                texture = null;
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void tryRestoreTexture(SurfaceTexture texture) {
        logger.info("Trying to restore texture");

        try {
            checkNotNull(texture, "Texture must not be null");
            if (surfaceView.getSurfaceTexture() != texture) {
                surfaceView.setSurfaceTexture(texture);
            }
        } catch (Exception err) {
            defaultOnError().call(err);
        }
    }

    private enum State {
        IDLE, PREPARING, PAUSED, PLAYING;

        public boolean after(State state) {
            return ordinal() > state.ordinal();
        }
    }
}
