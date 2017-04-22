package com.pr0gramm.app

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.instance
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.VoteService
import com.pr0gramm.app.services.preloading.PreloadManager
import com.pr0gramm.app.services.proxy.ProxyService
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.doInBackground

/**
 * Bootstraps a few instances in some other thread.
 */
object EagerBootstrap {
    fun initEagerSingletons(kodein: Kodein) {
        doInBackground {
            try {
                kodein.instance<ProxyService>()
                kodein.instance<PreloadManager>()
                kodein.instance<VoteService>()
                kodein.instance<UserService>()
            } catch (error: Throwable) {
                AndroidUtility.logToCrashlytics(error)
            }
        }
    }
}
