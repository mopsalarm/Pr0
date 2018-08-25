package com.pr0gramm.app

import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.VoteService
import com.pr0gramm.app.services.preloading.PreloadManager
import com.pr0gramm.app.services.proxy.ProxyService
import com.pr0gramm.app.sync.SyncService
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.logger
import com.pr0gramm.app.util.time
import org.kodein.di.DKodein
import org.kodein.di.erased.instance
import rx.Completable

/**
 * Bootstraps a few instances in some other thread.
 */
object EagerBootstrap {
    private val logger = logger("EagerBootstrap")

    fun initEagerSingletons(kodein: () -> DKodein): Completable {
        return doInBackground {
            try {
                logger.time("Bootstrapping instances...") {
                    kodein().apply {
                        instance<Api>()
                        instance<ProxyService>()
                        instance<PreloadManager>()
                        instance<VoteService>()
                        instance<UserService>()
                        instance<SyncService>()
                    }
                }
            } catch (error: Throwable) {
                AndroidUtility.logToCrashlytics(error)
            }
        }
    }
}
