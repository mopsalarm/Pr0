package com.pr0gramm.app.api.pr0gramm

import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Logger
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.Stats
import com.pr0gramm.app.util.catchAll
import com.squareup.moshi.adapter
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.BufferedSource
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class ApiProvider(base: String, client: OkHttpClient,
                  private val cookieJar: LoginCookieJar) {

    private val logger = Logger("ApiProvider")

    val api = proxy(restAdapter(base, client))

    private fun restAdapter(base: String, client: OkHttpClient): Api {
        return Retrofit.Builder()
                .baseUrl(base.toHttpUrl())
                .addConverterFactory(MoshiConverterFactory.create(MoshiInstance))
                .client(client.newBuilder().addInterceptor(ErrorInterceptor()).build())
                .validateEagerly(BuildConfig.DEBUG)
                .build().create()
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
                    args[0] = cookieJar.requireNonce()
                }
            }

            return doInvoke(method, args)
        }
    }

    private inner class ErrorInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())

            // if we get a valid "not allowed" error, we'll
            if (response.code == 403) {
                val body = response.peekBody(1024)
                val err = tryDecodeError(body.source())
                if (err?.error == "forbidden" && err.msg == "Not logged in") {
                    logger.warn { "Got 'Not logged in' error, will logout the user now." }
                    cookieJar.clearLoginCookie()

                    val key = err.msg.filter { it.isLetter() }
                    Stats().increment("api.forbidden", "message:$key")
                }
            }

            // everything looks good, just return the response as usual
            return response
        }

        private fun tryDecodeError(source: BufferedSource): Api.Error? {
            catchAll {
                return MoshiInstance.adapter<Api.Error>().fromJson(source)
            }

            return null
        }
    }
}
