package com.pr0gramm.app.api.pr0gramm

import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.pr0gramm.app.*
import com.pr0gramm.app.services.SingleShotService
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.util.Stopwatch
import kotlinx.coroutines.Deferred
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

class ApiProvider(base: String, client: OkHttpClient,
                  private val cookieHandler: LoginCookieHandler,
                  private val singleShotService: SingleShotService) {

    val api by lazy { proxy(restAdapter(base, client)) }

    private fun restAdapter(base: String, client: OkHttpClient): Api {
        val settings = Settings.get()

        val baseUrl = if (BuildConfig.DEBUG && settings.mockApi) {
            // activate this to use a mock
            HttpUrl.get("http://" + Debug.mockApiHost + ":8888")
        } else {
            HttpUrl.get(base)
        }

        return Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(MoshiConverterFactory.create(MoshiInstance))
                .addCallAdapterFactory(CoroutineCallAdapterFactory())
                .client(client)
                .validateEagerly(true)
                .build()
                .create(Api::class.java)
    }

    private fun proxy(backend: Api): Api {
        val apiClass = Api::class.java
        val proxy = Proxy.newProxyInstance(apiClass.classLoader, arrayOf(apiClass), InvocationHandler(backend))
        return proxy as Api
    }

    private inner class InvocationHandler(private val backend: Api) : java.lang.reflect.InvocationHandler {
        private fun doInvoke(method: Method, args: Array<out Any?>?): Any? {
            return try {
                if (args.isNullOrEmpty()) {
                    method.invoke(backend)
                } else {
                    method.invoke(backend, *args)
                }
            } catch (err: InvocationTargetException) {
                throw err.cause ?: err
            }
        }

        override fun invoke(proxy: Any, method: Method, args_: Array<Any?>?): Any? {
            var args = args_

            if (!args.isNullOrEmpty()) {
                if (method.parameterTypes[0] == Api.Nonce::class.java) {
                    args = args.copyOf()
                    args[0] = cookieHandler.nonce
                }
            }

            val watch = Stopwatch.createStarted()

            try {
                val result = doInvoke(method, args)
                if (result is Deferred<*>) {
                    result.invokeOnCompletion { err ->
                        if (err !is CancellationException) {
                            measureApiCall(watch, method, err == null)
                        }
                    }
                } else {
                    measureApiCall(watch, method, true)
                }

                return result

            } catch (err: Exception) {
                measureApiCall(watch, method, false)
                throw err
            }
        }
    }

    private fun measureApiCall(watch: Stopwatch, method: Method, success: Boolean) {
        val millis = watch.elapsed(TimeUnit.MILLISECONDS)

        Stats().time("api.call", millis, "method:${method.name}", "success:$success")

        // track only sync calls in 5% of syncs
        if (method.name == "sync" && Math.random() < 0.05) {
            Track.trackSyncCall(millis, success)
        }
    }
}
