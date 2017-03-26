package com.pr0gramm.app;

import android.app.Application;
import android.app.DownloadManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.PowerManager;

import com.pr0gramm.app.util.BackgroundScheduler;
import com.pr0gramm.app.util.Databases;
import com.pr0gramm.app.util.Holder;
import com.squareup.sqlbrite.BriteDatabase;
import com.squareup.sqlbrite.SqlBrite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import rx.Single;

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
    public BriteDatabase sqlBrite(SQLiteOpenHelper dbOpenHelper) {
        Logger logger = LoggerFactory.getLogger("SqlBrite");
        return new SqlBrite.Builder()
                .logger(logger::info)
                .build()
                .wrapDatabaseHelper(dbOpenHelper, BackgroundScheduler.instance());

    }


    @Provides
    @Singleton
    public Holder<SQLiteDatabase> databaseInstance(SQLiteOpenHelper dbOpenHelper) {
        Single<SQLiteDatabase> db = Single.fromCallable(dbOpenHelper::getWritableDatabase);
        return Holder.ofSingle(db.subscribeOn(BackgroundScheduler.instance()));
    }

    /**
     * Returns a single that returns the open helper
     */
    @Provides
    @Singleton
    public SQLiteOpenHelper dbOpenHelper(Application application) {
        return new Databases.PlainOpenHelper(application);
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
}
