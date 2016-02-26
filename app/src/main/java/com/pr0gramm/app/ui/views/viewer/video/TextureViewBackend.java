package com.pr0gramm.app.ui.views.viewer.video;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

/**
 * {@link ViewBackend} that uses a {@link TextureView}
 */
class TextureViewBackend implements ViewBackend, TextureView.SurfaceTextureListener {
    private final TextureView textureView;
    private final Callbacks callbacks;

    private Surface surface;

    public TextureViewBackend(Context context, Callbacks callbacks) {
        this.callbacks = callbacks;

        this.textureView = new TextureView(context);
        this.textureView.setSurfaceTextureListener(this);
    }

    @Override
    public void setSize(int width, int height) {
        // do nothing
    }

    @Override
    public void setSurface(MediaPlayer mp) {
        mp.setSurface(surface);
    }

    @Override
    public View getView() {
        return textureView;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        this.surface = new Surface(surface);
        callbacks.onAvailable(this);
        callbacks.onSizeChanged(this, width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        callbacks.onSizeChanged(this, width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        callbacks.onDestroy(this);

        if (this.surface != null) {
            this.surface.release();
            this.surface = null;
        }

        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    @Override
    public boolean hasSurface() {
        return surface != null;
    }
}
