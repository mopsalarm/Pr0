package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.util.Log;

import com.squareup.picasso.Downloader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import pl.droidsonroids.gif.GifDrawable;
import rx.Observable;
import rx.Subscriber;

import static java.lang.System.identityHashCode;

/**
 */
public class GifLoader implements Observable.OnSubscribe<GifLoader.DownloadStatus> {
    private final Downloader downloader;
    private final File temporaryPath;
    private final String url;

    GifLoader(Downloader downloader, File temporaryPath, String url) {
        this.downloader = downloader;
        this.temporaryPath = temporaryPath;
        this.url = url;
    }

    @Override
    public void call(Subscriber<? super GifLoader.DownloadStatus> subscriber) {
        try {
            // request the gif file
            Downloader.Response response = downloader.load(Uri.parse(url), 0);

            // and load + parse it
            loadGifUsingTempFile(subscriber, response);
        } catch (Exception error) {
            if (!subscriber.isUnsubscribed())
                subscriber.onError(error);
        }
    }

    /**
     * Loads the data of the gif into a temporary file. The method then
     * loads the gif from this temporary file. The temporary file is removed
     * after loading the gif (or on failure).
     */
    @SuppressLint("NewApi")
    private void loadGifUsingTempFile(Subscriber<? super GifLoader.DownloadStatus> subscriber,
                                      Downloader.Response response) throws IOException {

        File temporary = new File(temporaryPath, "tmp" + identityHashCode(subscriber) + ".gif");

        Log.i("Gif", "storing data into temporary file");
        RandomAccessFile storage = new RandomAccessFile(temporary, "rw");

        boolean close = true;
        try {
            // remove entry from filesystem now - the system will remove the data
            // when the stream closes.
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();

            // copy data to the file.
            long lastStatusTime = System.currentTimeMillis();
            try (InputStream stream = response.getInputStream()) {
                int length, count = 0;
                byte[] buffer = new byte[16 * 1024];
                while ((length = stream.read(buffer)) >= 0) {
                    storage.write(buffer, 0, length);
                    count += length;

                    if (subscriber.isUnsubscribed()) {
                        Log.i("GifLoader", "Stopped because the subscriber unsubscribed");
                        return;
                    }

                    // publish download progress every 100ms
                    long now = System.currentTimeMillis();
                    if (now - lastStatusTime > 250) {
                        subscriber.onNext(new DownloadStatus(count / (float) response.getContentLength()));
                        lastStatusTime = now;
                    }
                }
            }

            if (subscriber.isUnsubscribed())
                return;

            Log.i("Gif", "loading gif from file");
            GifDrawable drawable = new GifDrawable(storage.getFD());

            // closing is now delegated to the drawable.
            close = false;

            subscriber.onNext(new DownloadStatus(drawable));
            subscriber.onCompleted();

        } finally {
            if (close) {
                storage.close();
            }
        }
    }

    public static Observable<DownloadStatus> loader(
            Downloader downloader, File temporaryPath, String url) {

        return Observable.create(new GifLoader(downloader, temporaryPath, url));
    }

    public static class DownloadStatus {
        private final GifDrawable drawable;
        private final float progress;

        private DownloadStatus(float progress) {
            this.drawable = null;
            this.progress = progress;
        }

        private DownloadStatus(GifDrawable drawable) {
            this.drawable = drawable;
            this.progress = 1.f;
        }

        public float getProgress() {
            return progress;
        }

        public GifDrawable getDrawable() {
            return drawable;
        }

        public boolean isFinished() {
            return drawable != null;
        }
    }
}
