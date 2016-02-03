package com.pr0gramm.app.services;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;

import com.google.common.collect.ImmutableList;
import com.google.gson.GsonBuilder;
import com.pr0gramm.app.AppComponent;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.util.AndroidUtility;
import com.pr0gramm.app.util.BackgroundScheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.GsonConverterFactory;
import retrofit2.Retrofit;
import retrofit2.http.GET;
import rx.Observable;
import rx.util.async.Async;

/**
 * Class to perform an update check.
 */
public class UpdateChecker {
    public static final String KEY_DOWNLOAD_ID = "UpdateChecker.downloadId";

    private static final Logger logger = LoggerFactory.getLogger("UpdateChecker");

    private final int currentVersion;
    private final ImmutableList<String> endpoints;

    public UpdateChecker(Context context) {
        currentVersion = AndroidUtility.getPackageVersionCode(context);

        boolean betaChannel = Settings.of(context).useBetaChannel();
        endpoints = updateUrls(betaChannel);
    }

    private Observable<Update> check(String endpoint) {
        return Async.fromCallable(() -> {
            UpdateApi api = newRestAdapter(endpoint).create(UpdateApi.class);
            return api.get().execute().body();

        }, BackgroundScheduler.instance()).filter(update -> {
            logger.info("Installed v{}, found update v{} at {}",
                    currentVersion, update.version(), endpoint);

            // filter out if up to date
            return update.version() > currentVersion;
        }).map(update -> {
            // rewrite url to make it absolute
            String apk = update.apk();
            if (!apk.startsWith("http")) {
                apk = Uri.withAppendedPath(Uri.parse(endpoint), apk).toString();
            }

            logger.info("Got new update at url " + apk);
            return ImmutableUpdate.builder()
                    .version(update.version())
                    .changelog(update.changelog())
                    .apk(apk)
                    .build();
        });
    }

    public Observable<Update> check() {
        return Observable.from(endpoints)
                .flatMap(ep -> check(ep)
                        .doOnError(err -> logger.warn("Could not check for update at {}: {}", ep, err.toString()))
                        .onErrorResumeNext(Observable.empty()))
                .take(1);
    }

    private static Retrofit newRestAdapter(String endpoint) {
        com.google.gson.Gson gson = new GsonBuilder()
                .registerTypeAdapterFactory(new GsonAdaptersUpdate())
                .create();

        return new Retrofit.Builder()
                .baseUrl(endpoint)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
    }

    private interface UpdateApi {
        @GET("update.json")
        Call<Update> get();
    }

    /**
     * Returns the Endpoint-URL that is to be queried
     */
    private static ImmutableList<String> updateUrls(boolean betaChannel) {
        String flavor = BuildConfig.FLAVOR;
        List<String> urls = new ArrayList<>();

        if (betaChannel) {
            urls.add("https://github.com/mopsalarm/pr0gramm-updates/raw/beta/" + flavor + "/");
            urls.add("https://pr0.wibbly-wobbly.de/beta/" + flavor + "/");
        } else {
            urls.add("https://github.com/mopsalarm/pr0gramm-updates/raw/master/" + flavor + "/");
            urls.add("https://pr0.wibbly-wobbly.de/stable/" + flavor + "/");
        }

        return ImmutableList.copyOf(urls);
    }


    public static void download(Context context, Update update) {
        AppComponent appComponent = Dagger.appComponent(context);

        Uri apkUrl = Uri.parse(update.apk());

        DownloadManager.Request request = new DownloadManager.Request(apkUrl)
                .setVisibleInDownloadsUi(false)
                .setTitle(apkUrl.getLastPathSegment());

        long downloadId = appComponent.downloadManager().enqueue(request);

        appComponent.sharedPreferences().edit()
                .putLong(KEY_DOWNLOAD_ID, downloadId)
                .apply();

        // remove pending upload notification
        appComponent.notificationService().cancelForUpdate();
    }
}

