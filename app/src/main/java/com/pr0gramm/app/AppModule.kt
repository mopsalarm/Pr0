package com.pr0gramm.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.pr0gramm.app.util.BackgroundScheduler
import com.pr0gramm.app.util.Databases
import com.pr0gramm.app.util.Holder
import com.squareup.sqlbrite.BriteDatabase
import com.squareup.sqlbrite.SqlBrite
import org.kodein.di.Kodein
import org.kodein.di.erased.bind
import org.kodein.di.erased.instance
import org.kodein.di.erased.singleton
import org.slf4j.LoggerFactory
import rx.Single

/**
 */
fun appModule(app: Application) = Kodein.Module("app") {
    bind<SharedPreferences>() with singleton {
        app.getSharedPreferences("pr0gramm", Context.MODE_PRIVATE)
    }

    bind<Settings>() with singleton {
        Settings.get()
    }

    bind<SQLiteOpenHelper>() with singleton {
        Databases.PlainOpenHelper(app)
    }

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
