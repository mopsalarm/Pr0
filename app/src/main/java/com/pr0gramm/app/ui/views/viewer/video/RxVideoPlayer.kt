package com.pr0gramm.app.ui.views.viewer.video

import rx.Observable
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject

/**
 * This is a base for [VideoPlayer]s with some rx features.
 */
abstract class RxVideoPlayer : VideoPlayer {
    protected val detaches: PublishSubject<Unit> = PublishSubject.create()

    override var videoCallbacks: VideoPlayer.Callbacks? = null

    private val buffering = BehaviorSubject.create<Boolean>(false)

    fun buffering(): Observable<Boolean> {
        return buffering
    }

    fun detaches(): Observable<Unit> {
        return detaches
    }

    protected val callbacks: VideoPlayer.Callbacks = object : VideoPlayer.Callbacks {
        override fun onVideoBufferingStarts() {
            videoCallbacks?.onVideoBufferingStarts()
            buffering.onNext(true)
        }

        override fun onVideoBufferingEnds() {
            videoCallbacks?.onVideoBufferingEnds()
            buffering.onNext(false)
        }

        override fun onVideoRenderingStarts() {
            onVideoBufferingEnds()
            videoCallbacks?.onVideoRenderingStarts()
        }

        override fun onVideoSizeChanged(width: Int, height: Int) {
            videoCallbacks?.onVideoSizeChanged(width, height)
        }

        override fun onVideoError(message: String, kind: VideoPlayer.ErrorKind) {
            videoCallbacks?.onVideoError(message, kind)
        }

        override fun onDroppedFrames(count: Int) {
            videoCallbacks?.onDroppedFrames(count)
        }
    }
}
