package com.pr0gramm.app.util

import com.evernote.android.job.util.JobLogger

class SimpleJobLogger : JobLogger {
    private val logger = Logger("JobLogger")

    override fun log(priority: Int, tag: String, message: String, err: Throwable?) {
        logger.info { "$tag: $message" }

        if (err != null) {
            logger.error("Error in android-job", err)
        }
    }
}