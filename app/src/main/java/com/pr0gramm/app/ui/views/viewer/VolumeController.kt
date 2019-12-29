package com.pr0gramm.app.ui.views.viewer

import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.edit
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.catchAll
import com.pr0gramm.app.util.di.injector

class VolumeController(val view: ImageView, private val exo: () -> SimpleExoPlayer?) {
    private val logger = Logger("VolumeController")
    private val preferences = view.context.injector.instance<SharedPreferences>()

//    private val audioManager: AudioManager = view.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
//    private val afChangeListener = object : AudioManager.OnAudioFocusChangeListener {
//        override fun onAudioFocusChange(focusChange: Int) {
//            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || focusChange == AudioManager.AUDIOFOCUS_LOSS) {
//                audioManager.abandonAudioFocus(this)
//                logger.info { "Lost audio focus, muting now." }
//                setMuted(true)
//            }
//        }
//    }

    init {
        view.setOnClickListener { toggle() }
    }

    private fun toggle() {
        val exo = exo() ?: return
        val mutedCurrently = exo.volume < 0.1f
        setMuted(!mutedCurrently)
    }

    private fun setMuted(mute: Boolean): Unit = catchAll {
        //        if (muted) {
//            audioManager.abandonAudioFocus(afChangeListener)
//        } else {
//            val result = audioManager.requestAudioFocus(afChangeListener,
//                    AudioManager.STREAM_MUSIC, audioFocusDurationHint())
//
//            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
//                logger.info { "Did not get audio focus, muting now!" }
//                muted = true
//            }
//        }

        val exo = exo() ?: return

        logger.info { "Setting mute state on video player: $mute" }
        exo.volume = if (mute) 0f else 1f

        val icon: Drawable

        if (mute) {
            exo.volume = 0f

            storeUnmuteTime(0)
            icon = AppCompatResources.getDrawable(view.context, R.drawable.ic_volume_off_white_24dp)!!
        } else {
            val audioAttributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.CONTENT_TYPE_MOVIE)
                    .build()

            exo.setAudioAttributes(audioAttributes, true)

            exo.volume = 1f

            storeUnmuteTime(System.currentTimeMillis())

            icon = AndroidUtility.getTintedDrawable(view.context,
                    R.drawable.ic_volume_up_white_24dp, ThemeHelper.accentColor)
        }

        view.setImageDrawable(icon)
    }

    private fun audioFocusDurationHint(): Int {
        return if (Settings.get().audioFocusTransient) {
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        } else {
            AudioManager.AUDIOFOCUS_GAIN
        }
    }

    /**
     * Mute if not "unmuted" within the last 10 minutes.
     */
    fun applyMuteState() {
        val now = System.currentTimeMillis()
        val lastUnmutedVideo = preferences.getLong("VolumeController.lastUnmutedVideo", 0)
        val diffInSeconds = (now - lastUnmutedVideo) / 1000
        setMuted(diffInSeconds > 10 * 60)
    }

    private fun storeUnmuteTime(time: Long) {
        preferences.edit {
            putLong("VolumeController.lastUnmutedVideo", time)
        }
    }
}