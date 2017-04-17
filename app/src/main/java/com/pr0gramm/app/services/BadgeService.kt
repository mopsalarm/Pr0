package com.pr0gramm.app.services

import android.content.Context
import android.os.AsyncTask
import com.pr0gramm.app.util.AndroidUtility
import me.leolin.shortcutbadger.ShortcutBadger

/**
 * Service to wrap the badger class and allow only single threaded
 * access because of threading issues with the api :/
 */
class BadgeService {
    private val executor = AsyncTask.SERIAL_EXECUTOR

    fun update(context: Context, badgeCount: Int) {
        val appContext = context.applicationContext
        executor.execute {
            updateInternal(appContext, badgeCount)
        }
    }

    private fun updateInternal(appContext: Context, badgeCount: Int) {
        try {
            ShortcutBadger.applyCount(appContext, badgeCount)

        } catch (err: Throwable) {
            AndroidUtility.logToCrashlytics(err)
        }
    }
}
