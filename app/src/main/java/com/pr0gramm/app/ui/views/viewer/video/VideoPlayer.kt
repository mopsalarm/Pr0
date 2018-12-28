package com.pr0gramm.app.ui.views.viewer.video

import android.net.Uri

/**
 */
interface VideoPlayer {

    val progress: Float

    val buffered: Float

    val duration: Int

    val currentPosition: Int

    var videoCallbacks: VideoPlayer.Callbacks?

    var muted: Boolean

    fun open(uri: Uri)

    fun start()

    fun pause()

    fun rewind()

    fun seekTo(position: Int)

    interface Callbacks {
        fun onVideoBufferingStarts()

        fun onVideoBufferingEnds()

        fun onVideoRenderingStarts()

        fun onVideoSizeChanged(width: Int, height: Int)

        fun onVideoError(message: String, kind: ErrorKind)
    }

    enum class ErrorKind {
        UNKNOWN,
        NETWORK
    }
}
