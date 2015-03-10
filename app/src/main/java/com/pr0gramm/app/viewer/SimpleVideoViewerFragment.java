package com.pr0gramm.app.viewer;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.VideoView;

import com.pr0gramm.app.R;

import roboguice.inject.InjectView;

/**
 * Plays videos in a not optimal but compatible way.
 */
public class SimpleVideoViewerFragment extends ViewerFragment {
    private boolean playing;

    @InjectView(R.id.video)
    private VideoView videoView;

    public SimpleVideoViewerFragment() {
        super(R.layout.player_video_compat);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            mp.setVolume(0, 0);

            // scale view correctly
            float aspect = mp.getVideoWidth() / (float) mp.getVideoHeight();
            resizeViewerView(videoView, aspect, 10);

            hideBusyIndicator();
        });

        String url = getUrlArgument();
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
}
