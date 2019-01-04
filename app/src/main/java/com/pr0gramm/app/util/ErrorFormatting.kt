package com.pr0gramm.app.util

import android.content.Context
import androidx.annotation.StringRes
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.LoginCookieJar
import com.pr0gramm.app.ui.PermissionHelper
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import retrofit2.HttpException
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.*
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException

/**
 * This provides utilities for formatting of exceptions..
 */
object ErrorFormatting {
    @JvmStatic
    fun getFormatter(error: Throwable): Formatter {
        return formatters.firstOrNull { it.handles(error) }
                ?: throw IllegalStateException("There should always be a default formatter", error)
    }

    class Formatter internal constructor(private val errorCheck: (Throwable) -> Boolean,
                                         private val message: (Throwable, Context) -> String,
                                         private val report: Boolean = true) {

        /**
         * Tests if this formatter handles the given exception.
         */
        fun handles(thr: Throwable): Boolean = errorCheck(thr)

        /**
         * Gets the message for the given exception. You must only call this,
         * if [.handles] returned true before.
         */
        fun getMessage(context: Context, thr: Throwable): String {
            Logger("ErrorFormatting").warn("Formatting error:", thr)
            return message(thr, context)
        }

        /**
         * Returns true, if this exception should be logged
         */
        fun shouldSendToCrashlytics(): Boolean {
            return report
        }
    }

    private fun guessMessage(err: Throwable, context: Context): String {
        var message = err.localizedMessage
        if (message.isNullOrBlank())
            message = err.message

        if (message.isNullOrBlank())
            message = context.getString(R.string.error_exception_of_type, err.javaClass.simpleName)

        return message ?: err.toString()
    }

    private class Builder<out T : Throwable>(private val errorType: Class<in T>) {
        private val _errorCheck = mutableListOf<(T) -> Boolean>()

        private var _report: Boolean = true

        // by default just print the message of the exception.
        private var _message = { thr: T, ctx: Context -> guessMessage(thr, ctx) }

        fun silence() {
            _report = false
        }

        fun string(@StringRes id: Int) {
            _message = { _, ctx -> ctx.getString(id) }
        }

        fun format(fn: Context.(T) -> String) {
            _message = { thr, ctx -> ctx.fn(thr) }
        }

        fun check(fn: T.() -> Boolean) {
            _errorCheck += { it.fn() }
        }

        @Suppress("UNCHECKED_CAST")
        fun build(): Formatter {
            return Formatter(
                    errorCheck = { err -> errorType.isInstance(err) && _errorCheck.all { it(err as T) } },
                    message = { err, ctx -> _message(err as T, ctx) },
                    report = _report)
        }
    }

    private class FormatterList {
        val formatters = mutableListOf<Formatter>()

        inline fun <reified T : Throwable> add(configure: Builder<T>.() -> Unit) {
            formatters.add(Builder(T::class.java).apply(configure).build())
        }

        inline fun <reified T : Throwable> addCaused(configure: Builder<T>.() -> Unit) {
            val actual = Builder(T::class.java).apply(configure).build()

            formatters.add(Formatter(
                    errorCheck = { it.hasCauseOfType<T>() && actual.handles(it.getCauseOfType<T>()!!) },
                    message = { err, ctx -> actual.getMessage(ctx, err.getCauseOfType<T>()!!) },
                    report = actual.shouldSendToCrashlytics()
            ))
        }
    }

    /**
     * Returns a list containing multiple error formatters in the order they should
     * be applied.

     * @return The error formatters.
     */
    private fun makeErrorFormatters(): List<Formatter> {
        val formatters = FormatterList()

        formatters.add<HttpException> {
            silence()
            check { code() == 403 && "cloudflare" in bodyContent }
            string(R.string.error_cloudflare)
        }

        formatters.add<HttpException> {
            silence()
            check { code() == 403 && "<html>" in bodyContent }
            string(R.string.error_blocked)
        }

        formatters.add<HttpException> {
            silence()
            check { code() in listOf(401, 403) }
            string(R.string.error_not_authorized)
        }

        formatters.add<HttpException> {
            silence()
            check { code() == 429 }
            string(R.string.error_rate_limited)
        }

        formatters.add<HttpException> {
            silence()
            check { code() == 404 }
            string(R.string.error_not_found)
        }

        formatters.add<HttpException> {
            silence()
            check { code() == 504 }
            string(R.string.error_proxy_timeout)
        }

        formatters.add<HttpException> {
            silence()
            check { code() == 522 }
            string(R.string.error_origin_timeout_ddos)
        }

        formatters.add<HttpException> {
            silence()
            check { code() / 100 == 5 }
            string(R.string.error_service_unavailable)
        }

        formatters.add<JsonEncodingException> {
            string(R.string.error_json)
        }

        formatters.addCaused<FileNotFoundException> {
            silence()
            string(R.string.error_post_not_found)
        }

        formatters.addCaused<TimeoutException> {
            silence()
            string(R.string.error_timeout)
        }

        formatters.addCaused<SocketTimeoutException> {
            silence()
            string(R.string.error_timeout)
        }

        formatters.addCaused<JsonDataException> {
            silence()
            string(R.string.error_conversion)
        }

        formatters.addCaused<UnknownHostException> {
            silence()
            string(R.string.error_host_not_found)
        }

        formatters.addCaused<SSLException> {
            silence()
            string(R.string.error_ssl_error)
        }

        formatters.addCaused<ProtocolException> {
            string(R.string.error_protocol_exception)
        }

        formatters.addCaused<ConnectException> {
            silence()
            format { err ->
                if (":443" in err.toString()) {
                    getString(R.string.error_connect_exception_https, err.localizedMessage)
                } else {
                    getString(R.string.error_connect_exception, err.localizedMessage)
                }
            }
        }

        formatters.addCaused<SocketException> {
            silence()
            string(R.string.error_socket)
        }

        formatters.addCaused<EOFException> {
            silence()
            string(R.string.error_socket)
        }

        formatters.add<LoginCookieJar.LoginRequiredException> {
            string(R.string.error_login_required_exception)
        }

        formatters.add<IllegalStateException> {
            silence()
            check { "onSaveInstanceState" in toString() }
            string(R.string.error_generic)
        }

        formatters.add<IllegalStateException> {
            silence()
            check { ": Expected " in toString() }
            format { getString(R.string.error_json_mapping, it.message) }
        }

        formatters.add<StringException> {
            silence()
            format { it.messageProvider(this) }
        }

        formatters.add<PermissionHelper.PermissionNotGranted> {
            format {
                var permissionName: CharSequence = it.permission
                try {
                    val permissionInfo = packageManager.getPermissionInfo(it.permission, 0)
                    permissionName = permissionInfo.loadLabel(packageManager)
                } catch (err: Throwable) {
                }

                getString(R.string.error_permission_not_granted, permissionName)
            }
        }

        formatters.add<IOException> {
            silence()
        }

        formatters.addCaused<NullPointerException> {
            string(R.string.error_nullpointer)
        }

        formatters.addCaused<OutOfMemoryError> {
            string(R.string.error_oom)
        }

        formatters.add<Throwable> {}

        return formatters.formatters
    }

    inline fun <reified T : Throwable> Throwable.getCauseOfType(): T? {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) {
                return current
            }

            current = current.cause
        }

        return null
    }

    /**
     * Checks if the given throwable or any of it's causes is of the given type.
     */
    inline fun <reified T : Throwable> Throwable.hasCauseOfType(): Boolean {
        return getCauseOfType<T>() != null
    }

    fun format(context: Context, error: Throwable): String {
        return getFormatter(error).getMessage(context, error)
    }

    private val formatters = makeErrorFormatters()
}

private val HttpException.bodyContent: String
    get() {
        val body = this.response().errorBody() ?: return ""
        return kotlin.runCatching { body.string() }.getOrDefault("")
    }

class StringException(val messageProvider: (Context) -> String) : RuntimeException() {
    constructor(id: Int) : this({ it.getString(id) })
}
