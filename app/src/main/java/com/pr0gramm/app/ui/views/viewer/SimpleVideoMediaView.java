package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import com.google.common.base.Throwables;
import com.pr0gramm.app.R;
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment;

import java.io.IOException;

import roboguice.inject.InjectView;
import rx.functions.Actions;
import rx.util.async.Async;

/**
 * Plays videos in a not optimal but compatible way.
 */
@SuppressLint("ViewConstructor")
public class SimpleVideoMediaView extends MediaView implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnVideoSizeChangedListener, TextureView.SurfaceTextureListener {

    @InjectView(R.id.video)
    private TextureView surfaceView;

    private State targetState = State.IDLE;
    private State currentState = State.IDLE;
    private MediaPlayer mediaPlayer;

    private SurfaceTexture surface;

    public SimpleVideoMediaView(Context context, Binder binder, String url) {
        super(context, binder, R.layout.player_video_compat, url);

        Log.i(TAG, "Playing webm " + url);
        surfaceView.setSurfaceTextureListener(this);

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
        targetState = target;
        if (currentState == targetState)
            return;

        Log.i(TAG, "Moving from state " + currentState + " to " + targetState);

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
    }

    private void moveTo_Playing() {
        if (currentState == State.PAUSED) {
            if (surface != null) {
                mediaPlayer.setSurface(new Surface(surface));
                mediaPlayer.seekTo(0);
                surface = null;
            }

            mediaPlayer.start();
            currentState = State.PLAYING;
        }

        if (currentState == State.IDLE) {
            openMediaPlayer();
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
        if (mediaPlayer != null) {
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        surfaceView.setAlpha(0);
        showBusyIndicator();

        currentState = State.IDLE;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        currentState = State.PAUSED;
        if (targetState == State.IDLE) {
            moveTo(State.IDLE);
            return;
        }

        mp.setLooping(true);
        mp.setVolume(0, 0);

        hideBusyIndicator();
        surfaceView.setAlpha(1);

        // if we already have a surface, set it
        if (surface != null) {
            mediaPlayer.setSurface(new Surface(surface));
            surface = null;

            moveTo(targetState);
        }
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        // scale view correctly
        float aspect = width / (float) height;
        resizeViewerView(surfaceView, aspect, 10);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Exception error;
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_IO:
                error = new RuntimeException("Could not load data");
                break;

            case MediaPlayer.MEDIA_ERROR_MALFORMED:
                error = new RuntimeException("Malformed video data");
                break;

            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                error = new RuntimeException("Can not stream this format");
                break;

            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                error = new RuntimeException("The server did serving the video");
                break;

            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                error = new RuntimeException("Timeout error");
                break;

            default:
                error = new RuntimeException("Unknown error code " + what);
                break;
        }

        ErrorDialogFragment.defaultOnError().call(error);

        moveTo(State.IDLE);
        return true;
    }

    private void openMediaPlayer() {
        moveTo_Idle();
        currentState = State.PREPARING;

        mediaPlayer = new MediaPlayer();
        Async.fromCallable(() -> {
            try {
                mediaPlayer.setDataSource(getContext(), Uri.parse(url));
                mediaPlayer.setOnPreparedListener(this);
                mediaPlayer.setOnErrorListener(this);
                mediaPlayer.setOnVideoSizeChangedListener(this);
                mediaPlayer.prepareAsync();
                return mediaPlayer;

            } catch (IOException error) {
                throw Throwables.propagate(error);
            }
        }).subscribe(Actions.empty(), error -> {
            moveTo(State.IDLE);
            ErrorDialogFragment.defaultOnError().call(error);
        });
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.i(TAG, "Surface changed to " + surface);

        this.surface = surface;
        if (mediaPlayer != null && isPlaying()) {
            moveTo(State.PLAYING);
        }
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.i(TAG, "Surface destroyed " + surface);

        this.surface = null;
        if (mediaPlayer != null) {
            mediaPlayer.setSurface(null);
        }

        if (currentState != State.IDLE) {
            moveTo(isPlaying() ? State.PAUSED : State.IDLE);
        }

        return true;
    }

    private enum State {
        IDLE, PREPARING, PAUSED, PLAYING
    }
}
