package com.pr0gramm.app.ui.views.viewer.video;

import android.content.Context;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

/**
 */
public class SurfaceViewBackend implements ViewBackend, SurfaceHolder.Callback {
    private final Callbacks callbacks;
    private final SurfaceView surfaceView;

    public SurfaceViewBackend(Context context, Callbacks callbacks) {
        this.callbacks = callbacks;

        this.surfaceView = new SurfaceView(context);
        this.surfaceView.getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        callbacks.onAvailable(this);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        callbacks.onSizeChanged(this, width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        callbacks.onDestroy(this);
    }

    @Override
    public void setSize(int width, int height) {
        surfaceView.getHolder().setFixedSize(width, height);
    }

    @Override
    public Surface getCurrentSurface() {
        return surfaceView.getHolder().getSurface();
    }

    @Override
    public View getView() {
        return surfaceView;
    }

    @Override
    public boolean hasSurface() {
        Surface surface = surfaceView.getHolder().getSurface();
        return surface != null && surface.isValid();
    }
}
