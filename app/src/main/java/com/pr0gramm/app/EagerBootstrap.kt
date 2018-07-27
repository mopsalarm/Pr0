package com.pr0gramm.app

import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.VoteService
import com.pr0gramm.app.services.preloading.PreloadManager
import com.pr0gramm.app.services.proxy.ProxyService
import com.pr0gramm.app.sync.SyncService
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.time
import org.kodein.di.Kodein
import org.kodein.di.erased.instance
import org.slf4j.LoggerFactory
import rx.subjects.BehaviorSubject

/**
 * Bootstraps a few instances in some other thread.
 */
object EagerBootstrap {
    private val logger = LoggerFactory.getLogger("EagerBootstrap")

    private val c: BehaviorSubject<Unit> = BehaviorSubject.create()

    fun initEagerSingletons(kodein: Kodein) {
        doInBackground {
            try {
                logger.time("Bootstrapping instances...") {
                    kodein.instance<Api>()
                    kodein.instance<ProxyService>()
                    kodein.instance<PreloadManager>()
                    kodein.instance<VoteService>()
                    kodein.instance<UserService>()
                    kodein.instance<SyncService>()
                }
            } catch (error: Throwable) {
                AndroidUtility.logToCrashlytics(error)
            } finally {
                c.onCompleted()
            }
        }
    }

    fun ensureComplete() {
        if (!c.hasCompleted()) {
            // just wait for it to complete.
            c.toBlocking().subscribe()
        }
    }
}
