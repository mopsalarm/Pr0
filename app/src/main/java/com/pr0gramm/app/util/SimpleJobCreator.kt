package com.pr0gramm.app.util

import com.evernote.android.job.Job
import com.evernote.android.job.JobCreator

abstract class SimpleJobCreator protected constructor(private val tag: String) : JobCreator {
    override fun create(s: String): Job? {
        if (tag == s) {
            return create()
        } else {
            return null
        }
    }

    /**
     * Create the real job.
     */
    protected abstract fun create(): Job

    companion object {
        inline fun forSupplier(tag: String, crossinline creator: () -> Job): JobCreator {
            return object : SimpleJobCreator(tag) {
                override fun create(): Job {
                    return creator()
                }
            }
        }
    }
}
