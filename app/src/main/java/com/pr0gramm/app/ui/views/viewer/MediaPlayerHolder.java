package com.pr0gramm.app.ui.views.viewer;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import com.google.common.base.Optional;

import java.util.concurrent.atomic.AtomicReference;

/**
 */
public class MediaPlayerHolder {
    private final String url;
    private final MediaPlayer mediaPlayer;

    private MediaPlayerHolder(String url, MediaPlayer mediaPlayer) {
        this.url = url;
        this.mediaPlayer = mediaPlayer;
    }

    public String getUrl() {
        return url;
    }

    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    /**
     * Stores the given media instance for a short amount of time
     */
    public static void store(String url, MediaPlayer player) {
        clear();

        // store the player
        MediaPlayerHolder value = new MediaPlayerHolder(url, player);
        VALUE.compareAndSet(null, value);

        // and remove it, if no one else does
        HANDLER.postDelayed(() -> {
            if (VALUE.compareAndSet(value, null)) {
                clear(value);
            }
        }, 3000);
    }

    /**
     * Retrieves any player that is currently set.
     */
    public static Optional<MediaPlayerHolder> retrieve() {
        return Optional.fromNullable(VALUE.getAndSet(null));
    }

    private static void clear() {
        MediaPlayerHolder value = VALUE.getAndSet(null);
        if (value != null) {
            clear(value);
        }
    }

    private static void clear(MediaPlayerHolder value) {
        try {
            value.getMediaPlayer().reset();
            value.getMediaPlayer().release();
        } catch (Exception ignored) {
        }
    }

    private static final AtomicReference<MediaPlayerHolder> VALUE = new AtomicReference<>();
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
}
