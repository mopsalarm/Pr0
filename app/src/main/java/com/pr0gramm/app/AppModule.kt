package com.pr0gramm.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.github.salomonbrys.kodein.singleton
import com.pr0gramm.app.util.BackgroundScheduler
import com.pr0gramm.app.util.Databases
import com.pr0gramm.app.util.Holder
import com.squareup.sqlbrite.BriteDatabase
import com.squareup.sqlbrite.SqlBrite
import org.slf4j.LoggerFactory
import rx.Single

/**
 */
fun appModule(app: Application) = Kodein.Module {
    bind<SharedPreferences>(overrides = true) with instance(app.getSharedPreferences("pr0gramm", Context.MODE_PRIVATE))

    bind<Settings>() with instance(Settings.get())

    bind<SQLiteOpenHelper>() with instance(Databases.PlainOpenHelper(app))

    bind<Holder<SQLiteDatabase>>() with singleton {
        val helper: SQLiteOpenHelper = instance()
        val db = Single.fromCallable { helper.writableDatabase }
        Holder.ofSingle(db.subscribeOn(BackgroundScheduler.instance()))
    }

    bind<BriteDatabase>() with singleton {
        val logger = LoggerFactory.getLogger("SqlBrite")
        SqlBrite.Builder()
                .logger { logger.info("{}", it) }
                .build()
                .wrapDatabaseHelper(instance<SQLiteOpenHelper>(), BackgroundScheduler.instance())
    }
}
