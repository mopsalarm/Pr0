package com.pr0gramm.app.ui.views.viewer.video;

import android.media.MediaPlayer;
import android.view.View;

/**
 * Interface for a video backend.
 */
interface ViewBackend {
    void setSize(int width, int height);

    void setSurface(MediaPlayer mp);

    View getView();

    boolean hasSurface();

    /**
     * Callbacks that are send from a {@link ViewBackend}.
     */
    interface Callbacks {
        void onAvailable(ViewBackend backend);

        void onSizeChanged(ViewBackend backend, int width, int height);

        void onDestroy(ViewBackend backend);
    }
}
