package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.net.Uri;

import com.squareup.picasso.Downloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import pl.droidsonroids.gif.GifDrawable;
import rx.Observable;
import rx.Subscriber;

import static com.pr0gramm.app.util.AndroidUtility.toFile;
import static java.lang.System.identityHashCode;

/**
 */
public class GifLoader implements Observable.OnSubscribe<GifLoader.DownloadStatus> {
    private static final Logger logger = LoggerFactory.getLogger(GifLoader.class);

    private final Downloader downloader;
    private final File temporaryPath;
    private final Uri uri;

    GifLoader(Downloader downloader, File temporaryPath, Uri uri) {
        this.downloader = downloader;
        this.temporaryPath = temporaryPath;
        this.uri = uri;
    }

    @Override
    public void call(Subscriber<? super GifLoader.DownloadStatus> subscriber) {
        try {
            if ("file".equals(uri.getScheme())) {
                File file = toFile(uri);
                subscriber.onNext(new DownloadStatus(new GifDrawable(file)));
                subscriber.onCompleted();
                return;
            }

            // request the gif file
            Downloader.Response response = downloader.load(uri, 0);

            // and load + parse it
            loadGifUsingTempFile(subscriber, response);
        } catch (Throwable error) {
            logger.warn("Error during loading", error);

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

        logger.info("storing data into temporary file");
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
                byte[] buffer = new byte[4 * 1024];
                while ((length = stream.read(buffer)) >= 0) {
                    storage.write(buffer, 0, length);
                    count += length;

                    if (subscriber.isUnsubscribed()) {
                        logger.info("Stopped because the subscriber unsubscribed");
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

            try {
                GifDrawable drawable = new GifDrawable(storage.getFD());

                // closing is now delegated to the drawable.
                close = false;
                subscriber.onNext(new DownloadStatus(drawable));
            } catch (Throwable error) {
                subscriber.onError(error);
            }

            subscriber.onCompleted();

        } finally {
            if (close) {
                storage.close();
            }
        }
    }

    public static Observable<DownloadStatus> loader(
            Downloader downloader, File temporaryPath, Uri url) {

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
