package com.pr0gramm.app.api.pr0gramm

import android.app.Application
import android.content.Context
import com.google.common.base.Stopwatch
import com.google.common.reflect.Reflection
import com.google.common.util.concurrent.Uninterruptibles
import com.google.gson.Gson
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Debug
import com.pr0gramm.app.Settings
import com.pr0gramm.app.Stats
import com.pr0gramm.app.services.SingleShotService
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UriHelper
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import rx.Observable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

/**
 */
class ApiProvider(context: Application, client: OkHttpClient,
                  cookieHandler: LoginCookieHandler, gson: Gson,
                  private val singleShotService: SingleShotService) {

    val api = newProxyWrapper(newRestAdapter(context, client, gson), cookieHandler)

    private fun newProxyWrapper(backend: Api, cookieHandler: LoginCookieHandler): Api {
        // proxy to add the nonce if not provided
        return Reflection.newProxy(Api::class.java) { _, method, nullableArguments ->
            var args: Array<Any?> = nullableArguments ?: emptyArray()
            val watch = Stopwatch.createStarted()

            val params = method.parameterTypes
            if (params.isNotEmpty() && params[0] == Api.Nonce::class.java) {
                if (args.isNotEmpty() && args[0] == null) {

                    // inform about failure.
                    try {
                        args = args.copyOf()
                        args[0] = cookieHandler.nonce

                    } catch (error: Throwable) {
                        if (method.returnType === Observable::class.java) {
                            // don't fail during call but fail in the resulting observable.
                            return@newProxy Observable.error<Any>(error)

                        } else {
                            throw error
                        }
                    }
                }
            }

            var invoke = { method.invoke(backend, *args) }

            // wrap invoke into a retry for GET calls.
            if (method.returnType === Observable::class.java) {
                // only retry a get method, and only do it once.
                val retryCount = 1
                if (method.getAnnotation(GET::class.java) != null) {
                    invoke = { invokeWithRetry(backend, method, args, { isHttpError(it) }, retryCount) }
                }
            }

            try {
                var result = invoke()
                if (result is Observable<*>) {
                    result = result
                            .onErrorResumeNext { err -> Observable.error(convertErrorIfNeeded(err)) }
                            .doOnError { measureApiCall(watch, method, false) }
                            .doOnNext { measureApiCall(watch, method, true) }

                } else {
                    measureApiCall(watch, method, true)
                }

                result
            } catch (targetError: InvocationTargetException) {
                measureApiCall(watch, method, false)
                throw targetError.cause ?: targetError
            }
        }
    }

    private fun measureApiCall(watch: Stopwatch, method: Method, success: Boolean) {
        Stats.get().time("api.call", watch.elapsed(TimeUnit.MILLISECONDS),
                "method:" + method.name,
                "success:" + success)


        if ("sync".equals(method.name, ignoreCase = true) && singleShotService.firstTimeInHour("track-time:sync")) {
            // track only sync calls.
            Track.trackApiCallSpeed(watch, method.name, success)
        }
    }


    private fun newRestAdapter(context: Context, client: OkHttpClient, gson: Gson): Api {
        val settings = Settings.get()

        val baseUrl: HttpUrl
        if (BuildConfig.DEBUG && settings.mockApi) {
            // activate this to use a mock
            baseUrl = HttpUrl.parse("http://" + Debug.MOCK_API_HOST + ":8888")
        } else {
            baseUrl = HttpUrl.parse(UriHelper.of(context).base().toString())
        }

        return Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .client(client)
                .validateEagerly(true)
                .build()
                .create(Api::class.java)
    }

    internal fun convertErrorIfNeeded(err: Throwable): Throwable {
        if (err is retrofit2.HttpException) {
            val httpErr = err
            if (!httpErr.response().isSuccessful) {
                try {
                    val body = httpErr.response().errorBody().string()
                    return HttpErrorException(httpErr, body)
                } catch (ignored: Exception) {
                }
            }
        }

        return err
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeWithRetry(
            api: Api, method: Method, args: Array<Any?>,
            shouldRetryTest: (Throwable) -> Boolean, retryCount: Int): Observable<Any> {

        var result = method.invoke(api, *args) as Observable<Any>
        for (i in 0..retryCount - 1) {
            result = result.onErrorResumeNext { err ->
                try {
                    convertErrorIfNeeded(err).let { err ->
                        if (shouldRetryTest(err)) {
                            try {
                                // give the server a small grace period before trying again.
                                Uninterruptibles.sleepUninterruptibly(500, TimeUnit.MILLISECONDS)

                                logger.warn("perform retry, calling method {} again", method)
                                method.invoke(api, *args) as Observable<Any>
                            } catch (error: Exception) {
                                Observable.error <Any>(error)
                            }

                        } else {
                            // forward error if it is not a network problem
                            Observable.error <Any>(err)
                        }
                    }
                } catch (error: Throwable) {
                    // error while handling an error? oops!
                    Observable.error<Any>(error)
                }
            }
        }

        return result
    }


    private fun isHttpError(error: Throwable): Boolean {
        if (error is HttpErrorException) {
            val httpError = error
            val errorBody = httpError.errorBody

            logger.warn("Got http error {} {}, with body: {}", httpError.cause.code(),
                    httpError.cause.message(), errorBody)

            return httpError.cause.code() / 100 == 5
        } else {
            return false
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("ApiProvider")
    }
}
