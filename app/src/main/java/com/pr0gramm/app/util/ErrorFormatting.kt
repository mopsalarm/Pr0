package com.pr0gramm.app.util

import android.content.Context
import android.util.MalformedJsonException
import com.google.gson.JsonSyntaxException
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.HttpErrorException
import com.pr0gramm.app.api.pr0gramm.LoginCookieHandler
import com.pr0gramm.app.ui.PermissionHelper
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
        return Formatters.firstOrNull { it.handles(error) }
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
        fun getMessage(context: Context, thr: Throwable): String = message(thr, context)

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
        private var _report: Boolean = true

        private var _errorCheck = { _: T -> true }

        // by default just print the message of the exception.
        private var _message = { thr: T, ctx: Context -> guessMessage(thr, ctx) }

        fun silence() {
            _report = false
        }

        inline fun string(fn: () -> Int) {
            val key = fn()
            _message = { _, ctx -> ctx.getString(key) }
        }

        fun format(fn: Context.(T) -> String) {
            _message = { thr, ctx -> ctx.fn(thr) }
        }

        fun check(fn: T.() -> Boolean) {
            _errorCheck = { it.fn() }
        }

        inline fun <reified C : Throwable> hasCause() {
            _errorCheck = { err -> ErrorFormatting.hasCause<C>(err) }
        }

        @Suppress("UNCHECKED_CAST")
        fun build(): Formatter {
            return Formatter(
                    errorCheck = { errorType.isInstance(it) && _errorCheck(it as T) },
                    message = { err, ctx -> _message(err as T, ctx) },
                    report = _report)
        }
    }

    private class FormatterList {
        val formatters = mutableListOf<Formatter>()

        inline fun <reified T : Throwable> add(configure: Builder<T>.() -> Unit) {
            val b = Builder(T::class.java)
            b.configure()
            formatters.add(b.build())
        }

        inline fun <reified T : Throwable> addCaused(configure: Builder<T>.() -> Unit) {
            val b = Builder<T>(Throwable::class.java)
            b.hasCause<T>()
            b.configure()
            formatters.add(b.build())
        }
    }

    /**
     * Returns a list containing multiple error formatters in the order they should
     * be applied.

     * @return The error formatters.
     */
    private fun makeErrorFormatters(): List<Formatter> {
        val formatters = FormatterList()

        formatters.add<HttpErrorException> {
            silence()
            check { code == 403 && "cloudflare" in errorBody }
            string { R.string.error_cloudflare }
        }

        formatters.add<HttpErrorException> {
            silence()
            check { code == 403 && "<html>" in errorBody }
            string { R.string.error_blocked }
        }

        formatters.add<HttpErrorException> {
            silence()
            check { code in listOf(401, 403) }
            string { R.string.error_not_authorized }
        }

        formatters.add<HttpErrorException> {
            silence()
            check { code == 429 }
            string { R.string.error_rate_limited }
        }

        formatters.add<HttpErrorException> {
            silence()
            check { code == 404 }
            string { R.string.error_not_found }
        }

        formatters.add<HttpErrorException> {
            silence()
            check { code == 504 }
            string { R.string.error_proxy_timeout }
        }

        formatters.add<HttpErrorException> {
            silence()
            check { code == 522 }
            string { R.string.error_origin_timeout_ddos }
        }

        formatters.add<HttpErrorException> {
            silence()
            check { code / 100 == 5 }
            string { R.string.error_service_unavailable }
        }

        formatters.add<JsonSyntaxException> {
            string { R.string.error_json }
        }

        formatters.addCaused<FileNotFoundException> {
            silence()
            string { R.string.error_post_not_found }
        }

        formatters.addCaused<TimeoutException> {
            silence()
            string { R.string.error_timeout }
        }

        formatters.addCaused<SocketTimeoutException> {
            silence()
            string { R.string.error_timeout }
        }

        formatters.addCaused<MalformedJsonException> {
            silence()
            string { R.string.error_conversion }
        }

        formatters.addCaused<UnknownHostException> {
            silence()
            string { R.string.error_host_not_found }
        }

        formatters.addCaused<SSLException> {
            silence()
            string { R.string.error_ssl_error }
        }

        formatters.addCaused<ProtocolException> {
            string { R.string.error_protocol_exception }
        }

        formatters.addCaused<ConnectException> {
            silence()
            format {
                val err = getCause<ConnectException>(it)!!
                if (":443" in err.toString()) {
                    getString(R.string.error_connect_exception_https, err.localizedMessage)
                } else {
                    getString(R.string.error_connect_exception, err.localizedMessage)
                }
            }
        }

        formatters.addCaused<SocketException> {
            silence()
            string { R.string.error_socket }
        }

        formatters.addCaused<EOFException> {
            silence()
            string { R.string.error_socket }
        }

        formatters.add<LoginCookieHandler.LoginRequiredException> {
            string { R.string.error_login_required_exception }
        }

        formatters.add<IllegalStateException> {
            silence()
            check { "onSaveInstanceState" in toString() }
        }

        formatters.add<IllegalStateException> {
            silence()
            check { ": Expected " in toString() }
            format { getString(R.string.error_json_mapping, it.message) }
        }

        formatters.add<IllegalStateException> {
            silence()
            check { "onSaveInstanceState" in toString() }
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
            string { R.string.error_nullpointer }
        }

        formatters.addCaused<OutOfMemoryError> {
            string { R.string.error_oom }
        }

        formatters.add<Throwable> {}

        return formatters.formatters
    }

    inline private fun <reified T : Throwable> getCause(thr: Throwable?): T? {
        var current = thr
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
    inline private fun <reified T : Throwable> hasCause(thr: Throwable): Boolean {
        return getCause<T>(thr) != null
    }

    private val Formatters = makeErrorFormatters()
}
