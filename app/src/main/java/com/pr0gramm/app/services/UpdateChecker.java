package com.pr0gramm.app.services;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.FragmentActivity;

import com.google.common.collect.ImmutableList;
import com.google.gson.GsonBuilder;
import com.pr0gramm.app.AppComponent;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.ui.fragments.DownloadUpdateDialog;
import com.pr0gramm.app.util.AndroidUtility;
import com.pr0gramm.app.util.BackgroundScheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.util.async.Async;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;

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
            urls.add("https://pr0.wibbly-wobbly.de/beta/" + flavor + "/");
            urls.add("https://github.com/mopsalarm/pr0gramm-updates/raw/beta/" + flavor + "/");
        } else {
            urls.add("https://pr0.wibbly-wobbly.de/stable/" + flavor + "/");
            urls.add("https://github.com/mopsalarm/pr0gramm-updates/raw/master/" + flavor + "/");
        }

        return ImmutableList.copyOf(urls);
    }


    public static void download(FragmentActivity activity, Update update) {
        AppComponent appComponent = Dagger.appComponent(activity);
        Observable<DownloadService.Status> progress = appComponent.downloadService()
                .downloadToFile(update.apk())
                .subscribeOn(BackgroundScheduler.instance())
                .unsubscribeOn(BackgroundScheduler.instance())
                .observeOn(AndroidSchedulers.mainThread())
                .share();

        // install on finish
        Context appContext = activity.getApplicationContext();
        progress.filter(DownloadService.Status::finished)
                .subscribe(status -> install(appContext, status.file), defaultOnError());

        // show a progress dialog
        DownloadUpdateDialog dialog = new DownloadUpdateDialog(progress);
        dialog.show(activity.getSupportFragmentManager(), null);

        // remove pending upload notification
        appComponent.notificationService().cancelForUpdate();
    }

    private static void install(Context context, File apk) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(apk), "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}

