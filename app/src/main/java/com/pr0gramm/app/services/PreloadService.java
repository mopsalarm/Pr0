package com.pr0gramm.app.services;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.pr0gramm.app.AndroidUtility;
import com.pr0gramm.app.NotificationService;
import com.pr0gramm.app.R;
import com.pr0gramm.app.UriHelper;
import com.pr0gramm.app.feed.FeedItem;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import roboguice.service.RoboIntentService;
import rx.functions.Action1;

import static com.google.common.collect.Lists.newArrayList;

/**
 * This service handles preloading and resolving of preloaded images.
 */
public class PreloadService extends RoboIntentService {
    private static final Logger logger = LoggerFactory.getLogger(PreloadService.class);
    private static final String EXTRA_LIST_OF_ITEMS = "PreloadService.listOfItems";
    private static final String EXTRA_CANCEL = "PreloadService.cancel";

    private long jobId;
    private long lastShown;
    private volatile boolean canceled;

    @Inject
    private OkHttpClient httpClient;

    @Inject
    private NotificationManager notificationManager;

    @Inject
    private PreloadManager preloadManager;

    private File preloadCache;

    public PreloadService() {
        super("PreloadService");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        preloadCache = new File(getCacheDir(), "preload");
        if (preloadCache.mkdirs()) {
            logger.info("preload directory created at {}", preloadCache);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getLongExtra(EXTRA_CANCEL, -1) == jobId) {
            canceled = true;
            return START_NOT_STICKY;
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        List<FeedItem> items = intent.getExtras().getParcelableArrayList(EXTRA_LIST_OF_ITEMS);
        if (items == null || items.isEmpty())
            return;

        jobId = System.currentTimeMillis();
        canceled = false;

        PendingIntent contentIntent = PendingIntent.getService(this, 0,
                new Intent(this, PreloadService.class).putExtra(EXTRA_CANCEL, jobId),
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder noBuilder = new NotificationCompat.Builder(this)
                .setContentTitle("Preloading pr0gramm")
                .setSmallIcon(R.drawable.ic_notify_new_message)
                .setProgress(100 * items.size(), 0, false)
                .setOngoing(true)
                .setContentIntent(contentIntent);

        Instant creation = Instant.now();
        UriHelper uriHelper = UriHelper.of(this);

        // send out the initial notification
        show(noBuilder);
        try {
            int failed = 0, downloaded = 0;
            for (int idx = 0; idx < items.size() && !canceled; idx++) {
                FeedItem item = items.get(idx);
                try {
                    download(noBuilder, idx, items.size(), uriHelper.media(item));
                    download(noBuilder, idx, items.size(), uriHelper.thumbnail(item));

                    // store information about this entry in the database
                    preloadManager.store(ImmutablePreloadItem.builder()
                            .itemId(item.getId())
                            .creation(creation)
                            .media(cacheFileForUri(uriHelper.media(item)))
                            .thumbnail(cacheFileForUri(uriHelper.thumbnail(item)))
                            .build());

                    downloaded += 1;
                } catch (IOException ioError) {
                    failed += 1;
                    logger.warn("Could not preload image id=" + item.getId(), ioError);
                }
            }

            List<String> contentText = new ArrayList<>();
            contentText.add(String.format("%d files downloaded", downloaded));
            if (failed > 0) {
                contentText.add(String.format("%d failed", failed));
            }

            if (canceled) {
                contentText.add("canceled");
            }

            noBuilder.setContentText(Joiner.on(", ").join(contentText));

        } catch (Throwable error) {
            AndroidUtility.logToCrashlytics(error);
            noBuilder.setContentTitle("Preloading failed");

        } finally {
            logger.info("Finished preloading");
            show(noBuilder.setSmallIcon(R.drawable.ic_notify_preload_finished)
                    .setSubText(null)
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setContentIntent(null));
        }
    }

    private void download(NotificationCompat.Builder noBuilder, int index, int total, Uri uri) throws IOException {
        File targetFile = cacheFileForUri(uri);

        // if the file exists, we dont need to download it again
        if (targetFile.exists()) {
            logger.info("File {} already exists", targetFile);

            if (!targetFile.setLastModified(System.currentTimeMillis()))
                logger.warn("Could not touch file {}", targetFile);

            return;
        }

        File tempFile = new File(targetFile.getPath() + ".tmp");
        try {
            download(uri, tempFile, progress -> {
                String msg;
                int progressCurrent = (int) (100 * (index + progress));
                int progressTotal = 100 * total;

                if (canceled) {
                    msg = "Finishing";
                } else {
                    msg = "Fetching " + uri.getPath();
                }

                maybeShow(noBuilder.setContentText(msg).setProgress(progressTotal, progressCurrent, false));
            });

            if (!tempFile.renameTo(targetFile))
                throw new IOException("Could not rename file");

        } catch (Throwable error) {
            if (!tempFile.delete())
                logger.warn("Could not remove temporary file");

            Throwables.propagateIfInstanceOf(error, IOException.class);
            Throwables.propagateIfPossible(error);
            throw Throwables.propagate(error);
        }
    }

    private void show(NotificationCompat.Builder noBuilder) {
        notificationManager.notify(NotificationService.NOTIFICATION_PRELOAD_ID, noBuilder.build());
    }

    private void maybeShow(NotificationCompat.Builder builder) {
        long now = System.currentTimeMillis();
        if (now - lastShown > 500) {
            show(builder);
            lastShown = now;
        }
    }

    @SuppressLint("NewApi")
    private void download(Uri uri, File targetFile, Action1<Float> progress) throws IOException {
        logger.info("Start downloading {} to {}", uri, targetFile);

        Request request = new Request.Builder().get().url(uri.toString()).build();
        Response response = httpClient.newCall(request).execute();

        long contentLength = response.body().contentLength();

        try (InputStream inputStream = response.body().byteStream()) {
            try (OutputStream outputStream = new FileOutputStream(targetFile)) {
                if (contentLength < 0) {
                    progress.call(0.0f);
                    ByteStreams.copy(inputStream, outputStream);
                    progress.call(1.0f);
                } else {
                    copyWithProgress(progress, contentLength, inputStream, outputStream);
                }
            }
        }
    }


    /**
     * Name of the cache file for the given {@link Uri}.
     */
    private File cacheFileForUri(Uri uri) {
        String filename = uri.toString().replaceFirst("https?://", "").replaceAll("[^0-9a-zA-Z.]+", "_");
        return new File(preloadCache, filename);
    }

    /**
     * Copies from the input stream to the output stream.
     * The progress is written to the given observable.
     */
    private static void copyWithProgress(
            Action1<Float> progress, long contentLength,
            InputStream inputStream, OutputStream outputStream) throws IOException {

        long totalCount = 0;
        byte[] buffer = new byte[1024 * 64];

        int count;
        while ((count = ByteStreams.read(inputStream, buffer, 0, buffer.length)) > 0) {
            outputStream.write(buffer, 0, count);

            totalCount += count;
            progress.call((float) totalCount / contentLength);
        }
    }

    public static Intent newIntent(Context context, Iterable<FeedItem> items) {
        Intent intent = new Intent(context, PreloadService.class);
        intent.putParcelableArrayListExtra(EXTRA_LIST_OF_ITEMS, newArrayList(items));
        return intent;
    }
}
