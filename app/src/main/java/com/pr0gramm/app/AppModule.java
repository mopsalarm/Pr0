package com.pr0gramm.app;

import android.app.Application;
import android.app.DownloadManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.PowerManager;

import com.pr0gramm.app.services.preloading.DatabasePreloadManager;
import com.squareup.sqlbrite.BriteDatabase;
import com.squareup.sqlbrite.SqlBrite;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.util.async.Async;

/**
 */
@Module
public class AppModule {
    private final Application application;

    public AppModule(Application application) {
        this.application = application;
    }

    @Provides
    @Singleton
    public SharedPreferences sharedPreferences() {
        return application.getSharedPreferences("pr0gramm", Context.MODE_PRIVATE);
    }

    @Provides
    @Singleton
    public Observable<BriteDatabase> sqlBrite(Application application) {
        return Async.start(() -> {
            SQLiteOpenHelper openHelper = new OpenHelper(application);
            return SqlBrite.create().wrapDatabaseHelper(openHelper);
        }, Schedulers.io());
    }

    @Provides
    @Singleton
    public Application application() {
        return application;
    }

    @Provides
    @Singleton
    public Context context() {
        return application;
    }

    @Provides
    @Singleton
    public NotificationManager notificationManager() {
        return (NotificationManager) application.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Provides
    @Singleton
    public DownloadManager downloadManager() {
        return (DownloadManager) application.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    @Provides
    @Singleton
    public PowerManager powerManager() {
        return (PowerManager) application.getSystemService(Context.POWER_SERVICE);
    }

    private static class OpenHelper extends SQLiteOpenHelper {
        public OpenHelper(Context context) {
            super(context, "pr0-sqlbrite", null, 4);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            DatabasePreloadManager.onCreate(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onCreate(db);
        }
    }
}
