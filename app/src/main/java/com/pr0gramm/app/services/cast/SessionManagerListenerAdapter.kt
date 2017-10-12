package com.pr0gramm.app.services.cast

import com.google.android.gms.cast.framework.Session
import com.google.android.gms.cast.framework.SessionManagerListener

open class SessionManagerListenerAdapter<T : Session> : SessionManagerListener<T> {
    override fun onSessionEnding(session: T) {
    }

    override fun onSessionStarting(session: T) {
    }

    override fun onSessionEnded(session: T, error: Int) {
    }

    override fun onSessionResuming(session: T, sessionId: String) {
    }

    override fun onSessionResumed(session: T, wasSuspended: Boolean) {
    }

    override fun onSessionStartFailed(session: T, error: Int) {
    }

    override fun onSessionResumeFailed(session: T, error: Int) {
    }

    override fun onSessionStarted(session: T, sessionId: String) {
    }

    override fun onSessionSuspended(session: T, reason: Int) {
    }
}