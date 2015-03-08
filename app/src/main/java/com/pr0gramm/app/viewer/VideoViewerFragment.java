package com.pr0gramm.app.viewer;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.pr0gramm.app.MediaPlayerService;
import com.pr0gramm.app.R;

import roboguice.inject.InjectView;

import static java.lang.System.identityHashCode;

/**
 */
public class VideoViewerFragment extends ViewerFragment {
    private final String TAG = "Pr0Player " + Integer.toString(
            identityHashCode(this), Character.MAX_RADIX);

    private static final Handler handler = new Handler(Looper.getMainLooper());

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

            // size of the video
            resizeVideoView(mp.getVideoWidth() / (float) mp.getVideoHeight(), 10);

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

    /**
     * Resizes the video view while keeping the given aspect ratio.
     */
    private void resizeVideoView(float aspect, int retries) {
        Log.i(TAG, "Setting aspect of the TextureView to " + aspect);

        ViewParent parent = videoView.getParent();
        if (parent instanceof View) {
            int parentWidth = ((View) parent).getWidth();
            if (parentWidth == 0) {
                // relayout again in a short moment
                if (retries > 0) {
                    Log.i(TAG, "Delay resizing of TextureView for 100ms");
                    handler.postDelayed(() -> resizeVideoView(aspect, retries - 1), 100);
                }

                return;
            }

            int newHeight = (int) (parentWidth / aspect);
            if (videoView.getHeight() == newHeight) {
                Log.i(TAG, "View already correctly sized at " + parentWidth + "x" + newHeight);
                return;
            }

            Log.i(TAG, "Setting size of TextureView to " + parentWidth + "x" + newHeight);
            ViewGroup.LayoutParams params = videoView.getLayoutParams();
            params.height = newHeight;
            videoView.setLayoutParams(params);

        } else {
            Log.w(TAG, "TextureView has no parent, can not set size.");
        }
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
