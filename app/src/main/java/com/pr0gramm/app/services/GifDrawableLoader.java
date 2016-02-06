package com.pr0gramm.app.services;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;

import com.pr0gramm.app.services.proxy.ProxyService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import pl.droidsonroids.gif.GifDrawable;
import rx.Observable;
import rx.Subscriber;
import rx.subscriptions.Subscriptions;

import static com.pr0gramm.app.util.AndroidUtility.toFile;
import static java.lang.System.identityHashCode;

/**
 */
@Singleton
public class GifDrawableLoader {
    private static final Logger logger = LoggerFactory.getLogger("GifLoader");

    private final OkHttpClient okHttpClient;
    private final File fileCache;

    @Inject
    public GifDrawableLoader(Context context, OkHttpClient okHttpClient, ProxyService proxyService) {
        this.okHttpClient = okHttpClient;
        this.fileCache = context.getCacheDir();
    }

    public Observable<DownloadStatus> load(Uri uri) {
        return Observable.<DownloadStatus>create(subscriber -> {
            try {
                if ("file".equals(uri.getScheme())) {
                    File file = toFile(uri);
                    subscriber.onNext(new DownloadStatus(new GifDrawable(file)));
                    subscriber.onCompleted();
                    return;
                }

                // request the gif file
                Call call = okHttpClient.newCall(new Request.Builder()
                        .url(uri.toString())
                        .build());

                // stop the call on unsubscribe
                subscriber.add(Subscriptions.create(call::cancel));

                Response response = call.execute();
                if (response.isSuccessful()) {
                    loadGifUsingTempFile(subscriber, response);

                } else {
                    subscriber.onError(new IOException(
                            "Could not download gif, response code: " + response.code()));
                }
            } catch (Throwable error) {
                logger.warn("Error during loading", error);

                if (!subscriber.isUnsubscribed())
                    subscriber.onError(error);
            }
        }).serialize();
    }

    /**
     * Loads the data of the gif into a temporary file. The method then
     * loads the gif from this temporary file. The temporary file is removed
     * after loading the gif (or on failure).
     */
    @SuppressLint("NewApi")
    private void loadGifUsingTempFile(Subscriber<? super GifDrawableLoader.DownloadStatus> subscriber,
                                      Response response) throws IOException {

        File temporary = new File(fileCache, "tmp" + identityHashCode(subscriber) + ".gif");

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
            float contentLength = (float) response.body().contentLength();
            try (InputStream stream = response.body().byteStream()) {
                int length, count = 0;
                byte[] buffer = new byte[16 * 1024];
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
                        subscriber.onNext(new DownloadStatus(count / contentLength));
                        lastStatusTime = now;
                    }
                }
            }

            if (subscriber.isUnsubscribed())
                return;

            try {
                GifDrawable drawable = new GifDrawable(storage.getFD());

                // closing is now delegated to the drawable.
                subscriber.onNext(new DownloadStatus(drawable));
                subscriber.onCompleted();
            } catch (Throwable error) {
                subscriber.onError(error);
            }

        } finally {
            if (close) {
                storage.close();
            }
        }
    }

    public static class DownloadStatus {
        public final GifDrawable drawable;
        public final float progress;

        private DownloadStatus(float progress) {
            this.drawable = null;
            this.progress = progress;
        }

        private DownloadStatus(GifDrawable drawable) {
            this.drawable = drawable;
            this.progress = 1.f;
        }

        public boolean finished() {
            return drawable != null;
        }
    }
}
