package com.pr0gramm.app.ui.views.viewer.video;

import android.content.Context;
import android.media.MediaPlayer;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

/**
 */
public class SurfaceViewBackend implements ViewBackend, SurfaceHolder.Callback {
    private final SurfaceView surfaceView;
    private final Callbacks callbacks;
    private SurfaceHolder surfaceHolder;

    public SurfaceViewBackend(Context context, Callbacks callbacks) {
        this.callbacks = callbacks;

        this.surfaceView = new SurfaceView(context);
        this.surfaceView.getHolder().addCallback(this);
    }

    @Override
    public void setSize(int width, int height) {
        surfaceHolder.setFixedSize(width, height);
    }

    @Override
    public void setSurface(MediaPlayer mp) {
        mp.setSurface(surfaceHolder.getSurface());
    }

    @Override
    public View getView() {
        return surfaceView;
    }

    @Override
    public boolean hasSurface() {
        return surfaceHolder != null;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceHolder = holder;
        callbacks.onAvailable(this);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        callbacks.onSizeChanged(this, width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceHolder = null;
        callbacks.onDestroy(this);
    }
}
