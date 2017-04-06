package com.pr0gramm.app.services;

import android.annotation.TargetApi;
import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.Nullable;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.io.ByteStreams;
import com.google.common.io.CountingInputStream;
import com.google.common.io.PatternFilenameFilter;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.services.proxy.ProxyService;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import rx.Emitter;
import rx.Observable;
import rx.subscriptions.Subscriptions;

/**
 */
@Singleton
public class DownloadService {
    private static final Logger logger = LoggerFactory.getLogger("DownloadService");

    private final Context context;
    private final Settings settings;
    private final ProxyService proxyService;
    private final DownloadManager downloadManager;
    private final OkHttpClient okHttpClient;

    @Inject
    public DownloadService(DownloadManager downloadManager, ProxyService proxyService, Context context, OkHttpClient okHttpClient) {
        this.context = context;
        this.proxyService = proxyService;
        this.downloadManager = downloadManager;
        this.okHttpClient = okHttpClient;

        this.settings = Settings.of(context);
    }

    /**
     * Enqueues an object for download. If an error occurs this method returns
     * the error string. You can then display it as you please.
     */
    public Optional<String> download(FeedItem feedItem) {
        // download over proxy to use caching
        Uri url = proxyService.proxy(UriHelper.of(context).media(feedItem, true));

        File external;
        String location = settings.downloadLocation(context);
        if (location.equals(context.getString(R.string.pref_downloadLocation_value_downloads))) {
            external = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        } else if (location.equals(context.getString(R.string.pref_downloadLocation_value_pictures))) {
            external = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

        } else {
            external = Environment.getExternalStorageDirectory();
        }

        File targetDirectory = new File(external, "pr0gramm");
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            return Optional.of(context.getString(R.string.error_could_not_create_download_directory));
        }

        DateTimeFormatter format = DateTimeFormat.forPattern("yyyyMMdd-HHmmss");
        String fileType = feedItem.image().toLowerCase().replaceFirst("^.*\\.(\\w+)$", "$1");
        String prefix = Joiner.on("-").join(
                feedItem.created().toString(format),
                feedItem.user(),
                "id" + feedItem.id());

        String name = prefix.replaceAll("[^A-Za-z0-9_-]+", "") + "." + fileType;

        DownloadManager.Request request = new DownloadManager.Request(url);
        request.setVisibleInDownloadsUi(false);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setTitle(name);
        request.setDestinationUri(Uri.fromFile(new File(targetDirectory, name)));

        request.allowScanningByMediaScanner();

        downloadManager.enqueue(request);

        Track.download();
        return Optional.absent();
    }


    @TargetApi(Build.VERSION_CODES.KITKAT)
    public Observable<Status> downloadToFile(String uri) {
        return Observable.create(subscriber -> {
            try {
                File directory = new File(context.getCacheDir(), "updates");
                if (!directory.exists() && !directory.mkdirs()) {
                    logger.warn("Could not create apk directory: {}", directory);
                }

                // clear all previous files
                File[] files = directory.listFiles(new PatternFilenameFilter("pr0gramm-update.*apk"));
                for (File file : files) {
                    if (!file.delete()) {
                        logger.warn("Could not delete file {}", file);
                    }
                }

                // and download the new file.
                File tempFile = File.createTempFile(
                        "pr0gramm-update", ".apk",
                        directory);

                try (OutputStream output = new FileOutputStream(tempFile)) {
                    // now do the request
                    Request request = new Request.Builder().url(uri).build();
                    Call call = okHttpClient.newCall(request);
                    subscriber.setCancellation(call::cancel);

                    Response response = call.execute();
                    Interval interval = new Interval(250);
                    try (CountingInputStream input = new CountingInputStream(response.body().byteStream())) {
                        int count;
                        byte[] buffer = new byte[1024 * 32];
                        while ((count = ByteStreams.read(input, buffer, 0, buffer.length)) > 0) {
                            output.write(buffer, 0, count);

                            if (interval.check()) {
                                float progress = input.getCount() / (float) response.body().contentLength();
                                subscriber.onNext(new Status(progress, null));
                            }
                        }
                    }
                }

                subscriber.onNext(new Status(1, tempFile));
                subscriber.onCompleted();

            } catch (Throwable error) {
                subscriber.onError(error);
            }
        }, Emitter.BackpressureMode.LATEST);
    }

    private static class Interval {
        private final long interval;
        private long last = System.currentTimeMillis();

        Interval(long interval) {
            this.interval = interval;
        }

        public boolean check() {
            long now = System.currentTimeMillis();
            if (now - last > interval) {
                last = now;
                return true;
            }

            return false;
        }
    }

    public static class Status {
        @Nullable
        public final File file;
        public final float progress;

        Status(float progress, @Nullable File file) {
            this.progress = progress;
            this.file = file;
        }

        public boolean finished() {
            return file != null;
        }
    }
}
