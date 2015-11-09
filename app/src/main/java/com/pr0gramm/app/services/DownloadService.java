package com.pr0gramm.app.services;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.services.proxy.ProxyService;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 */
@Singleton
public class DownloadService {
    private final Context context;
    private final Settings settings;
    private final ProxyService proxyService;
    private final DownloadManager downloadManager;

    @Inject
    public DownloadService(DownloadManager downloadManager, ProxyService proxyService, Context context) {
        this.context = context;
        this.proxyService = proxyService;
        this.downloadManager = downloadManager;

        this.settings = Settings.of(context);
    }

    /**
     * Enqueues an object for download. If an error occures, this method returns
     * the error string. You can then display it as you please.
     */
    public Optional<String> download(FeedItem feedItem) {
        // download over proxy to use caching
        Uri url = proxyService.proxy(UriHelper.get().media(feedItem, true));

        File external;
        if (settings.downloadLocation().equals(context.getString(R.string.pref_downloadLocation_value_downloads))) {
            external = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        } else if (settings.downloadLocation().equals(context.getString(R.string.pref_downloadLocation_value_pictures))) {
            external = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

        } else {
            external = Environment.getExternalStorageDirectory();
        }

        File targetDirectory = new File(external, "pr0gramm");
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            return Optional.of(context.getString(R.string.error_could_not_create_download_directory));
        }

        DateTimeFormatter format = DateTimeFormat.forPattern("yyyyMMdd-HHmmss");
        String fileType = feedItem.getImage().toLowerCase().replaceFirst("^.*\\.([a-z]+)$", "$1");
        String prefix = Joiner.on("-").join(
                feedItem.getCreated().toString(format),
                feedItem.getUser(),
                "id" + feedItem.getId());

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
}
