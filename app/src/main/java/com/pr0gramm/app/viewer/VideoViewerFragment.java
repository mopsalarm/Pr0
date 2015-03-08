package com.pr0gramm.app.viewer;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import com.pr0gramm.app.MediaPlayerService;
import com.pr0gramm.app.R;

import roboguice.inject.InjectView;

import static java.lang.System.identityHashCode;

/**
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class VideoViewerFragment extends ViewerFragment {
    private final String TAG = "Pr0Player " + Integer.toString(
            identityHashCode(this), Character.MAX_RADIX);

    @InjectView(R.id.video)
    private TextureView videoView;

    private MediaPlayerService.MediaPlayerHolder holder;
    private VideoSurfaceTextureListener textureListener;

    public VideoViewerFragment() {
        super(R.layout.player_video);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Log.i(TAG, "Want to play video at " + this);
        if (holder != null) {
            Log.i(TAG, "Holder already exists.");

            videoView.setSurfaceTextureListener(textureListener);
            tryRestoreSurfaceTexture();
            return;
        }

        Log.i(TAG, "Create new MediaPlayer instance to play video");

        Context context = getActivity().getApplicationContext();
        holder = new MediaPlayerService.MediaPlayerHolder(context, getUrlArgument());
        textureListener = new VideoSurfaceTextureListener(holder);
        videoView.setSurfaceTextureListener(textureListener);
    }

    public void onResume() {
        super.onResume();

        Log.i(TAG, "onResume called");

        tryRestoreSurfaceTexture();

        holder.setOnPreparedListener(mp -> {
            Log.i(TAG, "MediaPlayer is prepared");

            // loop 10/10
            mp.setLooping(true);
            mp.setVolume(0, 0);

            // size of the video
            resizeViewerView(videoView, mp.getVideoWidth() / (float) mp.getVideoHeight(), 10);

            // start playback.
            holder.getPlayer().start();
            hideBusyIndicator();
        });
    }

    private void tryRestoreSurfaceTexture() {
        // if we are recreating the views, we need to re-attach the SurfaceTexture
        SurfaceTexture texture = textureListener.getTexture();
        if (textureListener.hasTexture() && videoView.getSurfaceTexture() != texture) {
            Log.i(TAG, "Restoring SurfaceTexture: " + texture);
            videoView.setSurfaceTexture(texture);
        }
    }

    @Override
    public void onPause() {
        Log.i(TAG, "onPause called");

        holder.setOnPreparedListener(null);

        if (holder.isPrepared()) {
            MediaPlayer player = holder.getPlayer();
            if (player.isPlaying())
                player.pause();
        }

        super.onPause();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy called");
        holder.destroy();
        textureListener.destroy();

        super.onDestroy();
    }

    private class VideoSurfaceTextureListener implements TextureView.SurfaceTextureListener {
        private final MediaPlayerService.MediaPlayerHolder holder;
        private SurfaceTexture texture;
        private boolean destroy;

        public VideoSurfaceTextureListener(MediaPlayerService.MediaPlayerHolder holder) {
            this.holder = holder;
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            Log.i(TAG, "SurfaceTexture available: " + texture);
            if (this.texture == null) {
                this.texture = texture;
                holder.getPlayer().setSurface(new Surface(texture));

            } else if (this.texture != texture) {
                Log.w(TAG, "Another TextureSurface became available - switching textures");
                holder.getPlayer().setSurface(new Surface(texture));
                this.texture = texture;
            }

            if (holder.isPrepared() && !holder.getPlayer().isPlaying()) {
                Log.i(TAG, "Starting playback");
                holder.getPlayer().start();
                hideBusyIndicator();
            }
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            if (holder.isPrepared()) {
                try {
                    MediaPlayer player = holder.getPlayer();
                    if (player.isPlaying())
                        player.pause();

                } catch (IllegalStateException ignored) {
                }
            }

            if (destroy) {
                Log.i(TAG, "Destroying SurfaceTexture: " + surface);
            } else {
                Log.i(TAG, "Ignoring destroy of SurfaceTexture: " + surface);
            }

            return destroy;
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            Log.i(TAG, "onSurfaceTextureSizeChanged");
            onSurfaceTextureAvailable(surface, width, height);
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }

        public SurfaceTexture getTexture() {
            return texture;
        }

        public boolean hasTexture() {
            return texture != null;
        }

        public void destroy() {
            destroy = true;

            if (texture != null) {
                texture.release();
                texture = null;
            }
        }
    }

}
