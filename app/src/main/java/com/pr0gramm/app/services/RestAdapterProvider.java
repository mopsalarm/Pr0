package com.pr0gramm.app.services;

import com.google.common.reflect.Reflection;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.pr0gramm.app.AndroidUtility;
import com.pr0gramm.app.LoggerAdapter;
import com.pr0gramm.app.LoginCookieHandler;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.Uris;
import com.pr0gramm.app.api.InstantDeserializer;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.squareup.okhttp.OkHttpClient;

import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.OkClient;
import retrofit.converter.GsonConverter;
import retrofit.http.GET;
import rx.Observable;

/**
 */
public class RestAdapterProvider implements Provider<Api> {
    private static final Logger logger = LoggerFactory.getLogger(RestAdapterProvider.class);

    private final Settings settings;
    private final OkHttpClient client;
    private final LoginCookieHandler cookieHandler;
    private final Api proxy;

    private Api backend;
    private boolean https;

    @Inject
    public RestAdapterProvider(Settings settings, OkHttpClient client, LoginCookieHandler cookieHandler) {
        this.settings = settings;
        this.client = client;
        this.cookieHandler = cookieHandler;

        this.proxy = newProxyWrapper();
    }

    private Api newRestAdapter() {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantDeserializer())
                .create();

        String host = Uris.of(settings).base().toString();

        // activate this to use a mock
        // host = "http://demo8733773.mockable.io";

        return new RestAdapter.Builder()
                .setEndpoint(host)
                .setConverter(new GsonConverter(gson))
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setLog(new LoggerAdapter(LoggerFactory.getLogger(Api.class)))
                .setClient(new OkClient(client))
                .build()
                .create(Api.class);
    }

    private synchronized Api getRestAdapter() {
        if (backend == null || https != settings.useHttps()) {
            backend = newRestAdapter();
            https = settings.useHttps();
        }

        return backend;
    }

    @Override
    public Api get() {
        return proxy;
    }

    private Api newProxyWrapper() {
        // proxy to add the nonce if not provided
        return Reflection.newProxy(Api.class, (proxy, method, args) -> {
            Class<?>[] params = method.getParameterTypes();
            if (params.length > 0 && params[0] == Api.Nonce.class) {
                if (args.length > 0 && args[0] == null) {

                    // inform about failure.
                    try {
                        args = Arrays.copyOf(args, args.length);
                        args[0] = cookieHandler.getNonce();

                    } catch (Throwable error) {
                        AndroidUtility.logToCrashlytics(error);

                        if (method.getReturnType() == Observable.class) {
                            // don't fail here, but fail in the resulting observable.
                            return Observable.error(error);

                        } else {
                            throw error;
                        }
                    }
                }
            }

            if (method.getReturnType() == Observable.class) {
                // check if this is a get for retry.
                GET get = method.getAnnotation(GET.class);
                final int retryCount = get != null ? 2 : 0;

                return invokeWithRetry(getRestAdapter(), method, args, retryCount);
            }

            try {
                return method.invoke(getRestAdapter(), args);
            } catch (InvocationTargetException targetError) {
                throw targetError.getCause();
            }
        });
    }

    @SuppressWarnings({"unchecked", "RedundantCast"})
    private static Observable<Object> invokeWithRetry(Api api, Method method, Object[] args, int retryCount)
            throws IllegalAccessException, InvocationTargetException {

        Observable<Object> result = (Observable<Object>) method.invoke(api, args);
        for (int i = 0; i < retryCount; i++) {
            result = result.onErrorResumeNext(err -> {
                if (isNetworkError(err)) {
                    try {
                        // give the server a small grace period before trying again.
                        Uninterruptibles.sleepUninterruptibly(500, TimeUnit.MILLISECONDS);

                        logger.warn("perform retry, calling method {} again", method);
                        return (Observable<Object>) method.invoke(api, args);
                    } catch (Exception error) {
                        return Observable.error(error);
                    }
                } else {
                    // forward error if it is not a network problem
                    return Observable.error(err);
                }
            });
        }

        return result;
    }


    private static boolean isNetworkError(Throwable error) {
        if (error instanceof RetrofitError) {
            RetrofitError httpError = (RetrofitError) error;

            RetrofitError.Kind kind = httpError.getKind();
            return kind == RetrofitError.Kind.HTTP || kind == RetrofitError.Kind.NETWORK;
        }

        return false;
    }
}
