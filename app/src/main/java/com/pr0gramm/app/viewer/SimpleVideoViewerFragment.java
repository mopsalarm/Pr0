package com.pr0gramm.app.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import android.widget.VideoView;

import com.pr0gramm.app.R;

import roboguice.inject.InjectView;

/**
 * Plays videos in a not optimal but compatible way.
 */
@SuppressLint("ViewConstructor")
public class SimpleVideoViewerFragment extends ViewerFragment implements MediaPlayer.OnPreparedListener {
    private boolean playing;

    @InjectView(R.id.video)
    private VideoView videoView;

    public SimpleVideoViewerFragment(Context context, Binder binder, String url) {
        super(context, binder, R.layout.player_video_compat, url);

        videoView.setOnPreparedListener(this);

        Log.i(TAG, "Playing webm " + url);
        videoView.setVideoURI(Uri.parse(url));
    }

    @Override
    public void onResume() {
        super.onResume();

        // continue playing
        if (playing)
            videoView.start();
    }

    @Override
    public void onPause() {
        videoView.pause();
        super.onPause();
    }

    @Override
    public void playMedia() {
        super.playMedia();
        Log.i(TAG, "Setting state to 'playing' now.");
        playing = true;

        if (videoView != null)
            videoView.start();
    }

    @Override
    public void stopMedia() {
        super.stopMedia();
        playing = false;

        if (videoView != null)
            videoView.pause();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mp.setLooping(true);
        mp.setVolume(0, 0);

        // scale view correctly
        float aspect = mp.getVideoWidth() / (float) mp.getVideoHeight();
        resizeViewerView(videoView, aspect, 10);

        hideBusyIndicator();
    }
}
