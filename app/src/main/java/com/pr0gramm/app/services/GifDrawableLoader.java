package com.pr0gramm.app.services;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build;

import com.pr0gramm.app.io.Cache;
import com.pr0gramm.app.services.proxy.ProxyService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import javax.inject.Inject;
import javax.inject.Singleton;

import pl.droidsonroids.gif.GifDrawable;
import rx.Emitter;
import rx.Observable;
import rx.Subscription;
import rx.subscriptions.Subscriptions;

import static com.pr0gramm.app.util.AndroidUtility.toFile;
import static java.lang.System.identityHashCode;

/**
 */
@Singleton
public class GifDrawableLoader {
    private static final Logger logger = LoggerFactory.getLogger("GifLoader");

    private final Cache cache;
    private final File fileCache;

    @Inject
    public GifDrawableLoader(Context context, Cache cache, ProxyService proxyService) {
        this.cache = cache;
        this.fileCache = context.getCacheDir();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public Observable<DownloadStatus> load(Uri uri) {
        return Observable.<DownloadStatus>create(emitter -> {
            try {
                if ("file".equals(uri.getScheme())) {
                    File file = toFile(uri);
                    emitter.onNext(new DownloadStatus(new GifDrawable(file)));
                    emitter.onCompleted();
                    return;
                }

                try (Cache.Entry entry = cache.get(uri)) {
                    loadGifUsingTempFile(emitter, entry);
                }
            } catch (Throwable error) {
                logger.warn("Error during gif-loading", error);
                emitter.onError(error);
            }
        }, Emitter.BackpressureMode.LATEST).serialize();
    }

    /**
     * Loads the data of the gif into a temporary file. The method then
     * loads the gif from this temporary file. The temporary file is removed
     * after loading the gif (or on failure).
     */
    @SuppressLint("NewApi")
    private void loadGifUsingTempFile(Emitter<? super DownloadStatus> subscriber,
                                      Cache.Entry entry) throws IOException {

        File temporary = new File(fileCache, "tmp" + identityHashCode(subscriber) + ".gif");

        logger.info("storing data into temporary file");

        Subscription subscription = Subscriptions.empty();
        subscriber.setSubscription(subscription);

        try (RandomAccessFile storage = new RandomAccessFile(temporary, "rw")) {
            // remove entry from filesystem now - the system will remove the data
            // when the stream closes.
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();

            // copy data to the file.
            long lastStatusTime = System.currentTimeMillis();
            float contentLength = (float) entry.totalSize();
            try (InputStream stream = entry.inputStreamAt(0)) {
                int length, count = 0;
                byte[] buffer = new byte[16 * 1024];
                while ((length = stream.read(buffer)) >= 0) {
                    storage.write(buffer, 0, length);
                    count += length;

                    if (subscription.isUnsubscribed()) {
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

            if (subscription.isUnsubscribed()) {
                return;
            }

            try {
                GifDrawable drawable = new GifDrawable(storage.getFD());

                // closing is now delegated to the drawable.
                subscriber.onNext(new DownloadStatus(drawable));
                subscriber.onCompleted();
            } catch (Throwable error) {
                subscriber.onError(error);
            }
        }
    }

    public static class DownloadStatus {
        public final GifDrawable drawable;
        public final float progress;

        DownloadStatus(float progress) {
            this.drawable = null;
            this.progress = progress;
        }

        DownloadStatus(GifDrawable drawable) {
            this.drawable = drawable;
            this.progress = 1.f;
        }

        public boolean finished() {
            return drawable != null;
        }
    }
}
