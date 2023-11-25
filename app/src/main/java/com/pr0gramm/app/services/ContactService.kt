package com.pr0gramm.app.services

import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Logger
import com.pr0gramm.app.Logging
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.util.ExceptionHandler
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Modifier
import java.util.Locale

/**
 * A simple service that allows sending a message to the pr0gramm support.
 */
class ContactService(private val api: Api) {
    suspend fun post(faqCategory: String, email: String, subject: String, message: String) {
        val version = BuildConfig.VERSION_NAME
        val androidVersion = Build.VERSION.RELEASE

        val msg = StringBuilder().run {
            append(message)
            append("\n\n")
            append("Gesendet mit der pr0gramm-app v$version auf Android $androidVersion")
            toString()
        }

        api.contactSend(faqCategory, subject, email, msg, extraText = DeviceInfoService.generate())
    }

    suspend fun report(itemId: Long, comment: Long, reason: String) {
        api.report(null, itemId, comment, reason)
    }

    private val logger = Logger("FeedbackService")
}


private object DeviceInfoService {
    fun generate(): String {
        val result = StringBuilder()

        fun add(name: String, block: (StringBuilder) -> Unit) {
            try {
                block(result)
            } catch (thr: Throwable) {
                result.append("Error while adding $name:")
                result.append(StringWriter().also { thr.printStackTrace(PrintWriter(it)) })
            }

            result.append("\n\n")
        }

        add("device info", this::appendDeviceInfo)
        add("memory info", this::appendMemoryInfo)
        add("codec info", this::appendCodecInfo)
        add("preferences", this::appendPreferences)
        add("previous stacktrace", this::appendPreviousStacktrace)
        add("log", this::appendLogMessages)

        // convert result to a string
        return result.toString()
    }

    private fun appendPreviousStacktrace(result: StringBuilder) {
        val trace = ExceptionHandler.previousStackTrace()
        if (trace != null) {
            result.append("previously recorded stack trace")
            result.append(trace)
        } else {
            result.append("no stack trace recorded")
        }
    }

    @OptIn(UnstableApi::class)
    private fun appendCodecInfo(result: StringBuilder) {
        for (info in MediaCodecUtil.getDecoderInfos("video/avc", false, false)) {
            result.append("codec: ").append(info.name).append("\n")
        }

        for (info in MediaCodecUtil.getDecoderInfos("video/vp9", false, false)) {
            result.append("codec: ").append(info.name).append("\n")
        }
    }

    private fun appendPreferences(result: StringBuilder) {
        Settings.raw().all.toSortedMap().forEach { (name, value) ->
            if (name.startsWith("pref_")) {
                result.append(name).append(": ").append(value).append("\n")
            }
        }
    }

    private fun appendLogMessages(result: StringBuilder) {
        Logging.recentMessages().forEach { message ->
            result.append(message).append('\n')
        }
    }

    private fun appendDeviceInfo(result: StringBuilder) {
        result.append("Android: ").append(Build.VERSION.RELEASE).append('\n')

        for (field in Build::class.java.fields) {
            if (Modifier.isStatic(field.modifiers)) {
                try {
                    val name = field.name.lowercase(Locale.getDefault()).replace('_', ' ')
                    val value = formatValue(field.get(null))
                    result.append(name).append(" = ").append(value).append("\n")
                } catch (ignored: Exception) {
                }
            }
        }
    }

    private fun formatValue(value: Any): String {
        if (value is Array<*>) {
            return value.toList().toString()
        } else {
            return value.toString()
        }
    }

    private fun appendMemoryInfo(result: StringBuilder) {
        val rt = Runtime.getRuntime()
        result.append("Memory used: ").append(rt.totalMemory() / 1024 / 1024).append("mb\n")
        result.append("MaxMemory for this app: ").append(rt.maxMemory() / 1024 / 1024).append("mb\n")
    }
}
