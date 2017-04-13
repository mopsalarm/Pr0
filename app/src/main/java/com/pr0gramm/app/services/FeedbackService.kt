package com.pr0gramm.app.services

import android.os.Build
import android.util.Base64
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil
import com.google.common.base.Charsets
import com.google.common.io.CharStreams
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Nothing
import com.pr0gramm.app.Settings
import com.pr0gramm.app.util.AndroidUtility
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import rx.Completable
import rx.Observable
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.lang.reflect.Modifier
import java.util.zip.DeflaterOutputStream

/**
 * A simple service to generate and send a feedback to the feedback server.
 */
class FeedbackService(okHttpClient: OkHttpClient) {

    private val api: Api = Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("https://pr0.wibbly-wobbly.de/api/feedback/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
            .build().create(Api::class.java)

    fun post(name: String, feedback: String): Completable {
        val version = AndroidUtility.buildVersionCode().toString()

        return Completable.defer {
            val logcat = payload()
            val bytes = ByteArrayOutputStream(logcat.length / 2).use { outputStream ->
                DeflaterOutputStream(outputStream).use { gzipStream ->
                    OutputStreamWriter(gzipStream, Charsets.UTF_8).use { writer ->
                        writer.write(logcat)
                    }
                }

                outputStream.toByteArray()
            }

            // rewrite the logcat.
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)

            logger.info("Sending feedback with {}bytes of logcat", encoded.length)
            api.post(name, feedback, version, encoded).toCompletable()
        }
    }

    private fun payload(): String {
        try {
            val result = StringBuilder()

            appendDeviceInfo(result)
            result.append("\n\n")

            appendMemoryInfo(result)
            result.append("\n\n")

            appendCodecInfo(result)
            result.append("\n\n")

            appendPreferences(result)
            result.append("\n\n")

            appendLogcat(result)

            // convert result to a string
            return result.toString()

        } catch (err: Exception) {
            return "Could not generate logcat: " + err
        }
    }

    private fun appendCodecInfo(result: StringBuilder) {
        try {
            val decoderInfos = MediaCodecUtil.getDecoderInfos("video/avc", false)
            for (info in decoderInfos) {
                result.append("codec: ").append(info.name).append("\n")
            }

        } catch (ignored: MediaCodecUtil.DecoderQueryException) {
            result.append("codec: could not query codecs.\n")
        }

    }

    private fun appendPreferences(result: StringBuilder) {
        Settings.get().raw().all.forEach { name, value ->
            if (name.startsWith("pref_")) {
                result.append(name).append(": ").append(value).append("\n")
            }
        }
    }

    private interface Api {
        @FormUrlEncoded
        @POST("post")
        fun post(@Field("name") name: String,
                 @Field("feedback") feedback: String,
                 @Field("version") version: String,
                 @Field("logcat64") logcat: String): Observable<Nothing>
    }


    @Throws(IOException::class)
    private fun appendLogcat(result: StringBuilder) {
        val process = Runtime.getRuntime().exec("logcat -d -v threadtime")
        try {
            CharStreams.asWriter(result).use { writer ->
                val reader = InputStreamReader(process.inputStream, Charsets.UTF_8)
                CharStreams.copy(reader, writer)
            }
        } finally {
            try {
                process.destroy()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun appendDeviceInfo(result: StringBuilder) {
        result.append("Android: ").append(Build.VERSION.RELEASE).append('\n')

        result.append("Flavor: ").append(BuildConfig.FLAVOR)
                .append("(").append(BuildConfig.APPLICATION_ID).append(")\n")

        for (field in Build::class.java.fields) {
            if (Modifier.isStatic(field.modifiers)) {
                try {
                    val name = field.name.toLowerCase().replace('_', ' ')
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

    companion object {
        private val logger = LoggerFactory.getLogger("FeedbackService")
    }
}
