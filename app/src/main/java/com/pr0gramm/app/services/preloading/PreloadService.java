package com.pr0gramm.app.services.preloading;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.R;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.services.NotificationService;
import com.pr0gramm.app.services.UriHelper;
import com.pr0gramm.app.util.AndroidUtility;
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

import javax.inject.Inject;

import rx.functions.Action1;

import static com.google.common.collect.Lists.newArrayList;
import static com.pr0gramm.app.util.AndroidUtility.toFile;
import static org.joda.time.Duration.standardDays;
import static org.joda.time.Minutes.minutes;

/**
 * This service handles preloading and resolving of preloaded images.
 */
public class PreloadService extends IntentService {
    private static final Logger logger = LoggerFactory.getLogger(PreloadService.class);
    private static final String EXTRA_LIST_OF_ITEMS = "PreloadService.listOfItems";
    private static final String EXTRA_CANCEL = "PreloadService.cancel";

    private long jobId;
    private long lastShown;
    private volatile boolean canceled;

    @Inject
    OkHttpClient httpClient;

    @Inject
    NotificationManager notificationManager;

    @Inject
    PreloadManager preloadManager;

    @Inject
    PowerManager powerManager;

    private File preloadCache;

    public PreloadService() {
        super("PreloadService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Dagger.appComponent(this).inject(this);

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

        // create a wake lock
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, PreloadService.class.getName());

        // send out the initial notification and bring the service into foreground mode!
        startForeground(NotificationService.NOTIFICATION_PRELOAD_ID, noBuilder.build());

        try {
            logger.info("Acquire wake lock for at most 10 minutes");
            wakeLock.acquire(minutes(10).toStandardDuration().getMillis());

            int failed = 0, downloaded = 0;
            for (int idx = 0; idx < items.size() && !canceled; idx++) {
                if (AndroidUtility.isOnMobile(this))
                    break;

                FeedItem item = items.get(idx);
                try {
                    Uri mediaUri = uriHelper.media(item);
                    boolean mediaIsLocal = "file".equals(mediaUri.getScheme());
                    File mediaFile = mediaIsLocal ? toFile(mediaUri) : cacheFileForUri(mediaUri);

                    Uri thumbUri = uriHelper.thumbnail(item);
                    boolean thumbIsLocal = "file".equals(thumbUri.getScheme());
                    File thumbFile = thumbIsLocal ? toFile(thumbUri) : cacheFileForUri(thumbUri);

                    // prepare the entry that will be put into the database later
                    PreloadManager.PreloadItem entry = ImmutablePreloadItem.builder()
                            .itemId(item.getId())
                            .creation(creation)
                            .media(mediaFile)
                            .thumbnail(thumbFile)
                            .build();

                    maybeShow(noBuilder.setProgress(items.size(), idx, false));

                    if (!mediaIsLocal)
                        download(noBuilder, 2 * idx, 2 * items.size(), mediaUri, entry.media());

                    if (!thumbIsLocal)
                        download(noBuilder, 2 * idx + 1, 2 * items.size(), thumbUri, entry.thumbnail());

                    preloadManager.store(entry);

                    downloaded += 1;
                } catch (IOException ioError) {
                    failed += 1;
                    logger.warn("Could not preload image id=" + item.getId(), ioError);
                }
            }

            // doing cleanup
            doCleanup(noBuilder, Instant.now().minus(standardDays(1)));

            // setting end message
            showEndMessage(noBuilder, downloaded, failed);

        } catch (Throwable error) {
            AndroidUtility.logToCrashlytics(error);
            noBuilder.setContentTitle("Preloading failed");

        } finally {
            try {
                logger.info("Releasing wake lock");
                wakeLock.release();
            } catch (RuntimeException ignored) {
            }

            logger.info("Finished preloading");
            show(noBuilder.setSmallIcon(R.drawable.ic_notify_preload_finished)
                    .setSubText(null)
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setContentIntent(null));

            stopForeground(false);
        }
    }

    private void showEndMessage(NotificationCompat.Builder noBuilder, int downloaded, int failed) {
        List<String> contentText = new ArrayList<>();
        contentText.add(String.format("%d files downloaded", downloaded));
        if (failed > 0) {
            contentText.add(String.format("%d failed", failed));
        }

        if (canceled) {
            contentText.add("canceled");
        }

        noBuilder.setContentText(Joiner.on(", ").join(contentText));
    }

    /**
     * Cleaning old files before the given threshold.
     */
    private void doCleanup(NotificationCompat.Builder noBuilder, Instant threshold) {
        show(noBuilder
                .setContentText("Cleaning up old files")
                .setProgress(0, 0, true));

        preloadManager.deleteBefore(threshold);
    }

    private void download(NotificationCompat.Builder noBuilder, int index, int total,
                          Uri uri, File targetFile) throws IOException {

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
