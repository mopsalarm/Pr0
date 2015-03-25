package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import com.pr0gramm.app.R;

import roboguice.inject.InjectView;

/**
 * Plays videos in a not optimal but compatible way.
 */
@SuppressLint("ViewConstructor")
public class SimpleVideoMediaView extends MediaView implements MediaPlayer.OnPreparedListener {
    @InjectView(R.id.video)
    private SimplifiedAndroidVideoView videoView;

    public SimpleVideoMediaView(Context context, Binder binder, String url) {
        super(context, binder, R.layout.player_video_compat, url);

        videoView.setOnPreparedListener(this);

        Log.i(TAG, "Playing webm " + url);
        videoView.setAlpha(0);
        videoView.setVideoURI(Uri.parse(url));
    }

    @Override
    public void onResume() {
        super.onResume();
        startVideoPlayback();
    }

    private void startVideoPlayback() {
        if (videoView != null && isPlaying())
            videoView.start();
    }

    @Override
    public void onPause() {
        pauseVideoPlayback();
        super.onPause();
    }

    private void pauseVideoPlayback() {
        if (videoView == null)
            return;

        videoView.pause();
    }

    @Override
    public void playMedia() {
        super.playMedia();
        Log.i(TAG, "Setting state to 'playing' now.");

        startVideoPlayback();
    }

    @Override
    public void stopMedia() {
        super.stopMedia();
        pauseVideoPlayback();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mp.setLooping(true);
        mp.setVolume(0, 0);

        // scale view correctly
        float aspect = mp.getVideoWidth() / (float) mp.getVideoHeight();
        resizeViewerView(videoView, aspect, 10);

        hideBusyIndicator();
        videoView.setAlpha(1);
    }
}
