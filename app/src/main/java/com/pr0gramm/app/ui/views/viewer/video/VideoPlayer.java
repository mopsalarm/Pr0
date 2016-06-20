package com.pr0gramm.app.ui.views.viewer.video;

import android.net.Uri;
import android.support.annotation.Nullable;

/**
 */
public interface VideoPlayer {
    void setVideoCallbacks(@Nullable VideoPlayer.Callbacks callbacks);

    void open(Uri uri);

    void start();

    float progress();

    float buffered();

    void pause();

    void rewind();

    boolean isMuted();

    void setMuted(boolean muted);

    interface Callbacks {
        void onVideoBufferingStarts();

        void onVideoBufferingEnds();

        void onVideoRenderingStarts();

        void onVideoSizeChanged(int width, int height);

        void onVideoError(String message, ErrorKind kind);

        void onDroppedFrames(int count);
    }

    enum ErrorKind {
        UNKNOWN,
        NETWORK
    }
}
