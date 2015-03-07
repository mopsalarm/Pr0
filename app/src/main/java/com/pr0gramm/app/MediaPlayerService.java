package com.pr0gramm.app;

import android.app.Application;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;

import com.google.common.base.Throwables;
import com.google.inject.Singleton;
import com.squareup.okhttp.OkHttpClient;

import java.io.IOException;

import javax.inject.Inject;

import static com.google.common.base.Objects.equal;

/**
 */
@Singleton
public class MediaPlayerService {
    private final Application application;
    private final OkHttpClient okHttpClient;

    private MediaPlayerHolder previous;

    @Inject
    public MediaPlayerService(Application application, OkHttpClient okHttpClient) {
        this.application = application;
        this.okHttpClient = okHttpClient;
    }

    public MediaPlayerHolder getMediaPlayer(String url) throws IOException {
        if (previous != null && equal(previous.getUrl(), url))
            return previous;

        if (previous != null) {
            previous.release();
            previous = null;
        }

        MediaPlayerHolder holder = new MediaPlayerHolder(application, url);
        previous = holder;
        return holder;
    }

    public static class MediaPlayerHolder implements MediaPlayer.OnPreparedListener {
        private final MediaPlayer player;
        private final String url;
        private boolean prepared;
        private MediaPlayer.OnPreparedListener onPreparedListener;

        public MediaPlayerHolder(Context context, String url) {
            this.url = url;
            player = new MediaPlayer();
            setDataSource(context, url);

            player.setOnPreparedListener(this);
            player.prepareAsync();
        }

        /**
         * Sets the datasource by calling {@link android.media.MediaPlayer#setDataSource(android.content.Context, android.net.Uri)}.
         * This method catches all {@link java.io.IOException} that might be thrown and
         * rethrows them as unchecked exceptions.
         *
         * @param context The context to use for resolving the url.
         * @param url     The url to set on the player.
         */
        private void setDataSource(Context context, String url) {
            try {
                player.setDataSource(context, Uri.parse(url));
            } catch (IOException err) {
                throw Throwables.propagate(err);
            }
        }

        public MediaPlayer getPlayer() {
            return player;
        }

        public String getUrl() {
            return url;
        }

        public boolean isPrepared() {
            return prepared;
        }

        public MediaPlayer.OnPreparedListener getOnPreparedListener() {
            return onPreparedListener;
        }

        public void setOnPreparedListener(MediaPlayer.OnPreparedListener onPreparedListener) {
            this.onPreparedListener = onPreparedListener;

            if (prepared && onPreparedListener != null) {
                this.onPreparedListener = null;
                onPreparedListener.onPrepared(player);
            }
        }

        @Override
        public void onPrepared(MediaPlayer mp) {
            prepared = true;

            if (onPreparedListener != null)
                onPreparedListener.onPrepared(mp);
        }

        void release() {
            player.release();
        }

        public void destroy() {
            player.release();
            // previous = null;
        }
    }
}
