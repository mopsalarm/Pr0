package com.pr0gramm.app.sync

import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import com.pr0gramm.app.util.AndroidUtility
import java.util.concurrent.CancellationException

inline fun CoroutineWorker.retryOnError(block: () -> ListenableWorker.Result): ListenableWorker.Result {
    return try {
        block()

    } catch (err: CancellationException) {
        throw err

    } catch (err: Exception) {
        AndroidUtility.logToCrashlytics(err)
        ListenableWorker.Result.retry()
    }
}

