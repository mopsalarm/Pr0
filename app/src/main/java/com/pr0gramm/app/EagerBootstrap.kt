package com.pr0gramm.app

import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.VoteService
import com.pr0gramm.app.services.preloading.PreloadManager
import com.pr0gramm.app.services.proxy.ProxyService
import com.pr0gramm.app.sync.SyncService
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.Logger
import com.pr0gramm.app.util.time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kodein.di.DKodein
import org.kodein.di.erased.instance

/**
 * Bootstraps a few instances in some other thread.
 */
object EagerBootstrap {
    private val logger = Logger("EagerBootstrap")

    suspend fun initEagerSingletons(kodein: DKodein) {
        withContext(Dispatchers.IO) {
            try {
                logger.time("Bootstrapping eager singleton instances in background...") {
                    kodein.apply {
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
