package com.pr0gramm.app.api.pr0gramm;

import android.content.Context;

import com.google.common.base.Predicate;
import com.google.common.io.CharStreams;
import com.google.common.reflect.Reflection;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.gson.Gson;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.UriHelper;
import com.pr0gramm.app.util.AndroidUtility;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.ResponseBody;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import retrofit.BaseUrl;
import retrofit.Converter;
import retrofit.GsonConverterFactory;
import retrofit.HttpException;
import retrofit.Retrofit;
import retrofit.RxJavaCallAdapterFactory;
import retrofit.http.GET;
import rx.Observable;

/**
 */
@Singleton
public class ApiProvider implements Provider<Api> {
    private static final Logger logger = LoggerFactory.getLogger(ApiProvider.class);

    private final Context context;
    private final Settings settings;
    private final OkHttpClient client;
    private final LoginCookieHandler cookieHandler;
    private final Api proxy;

    @Inject
    public ApiProvider(Context context, OkHttpClient client, LoginCookieHandler cookieHandler) {
        this.context = context;
        this.settings = Settings.of(context);
        this.client = client;
        this.cookieHandler = cookieHandler;

        this.proxy = newProxyWrapper();
    }

    private Api newRestAdapter() {
        Gson gson = ApiGsonBuilder.builder().create();

        BaseUrl baseUrl = () -> {
            if (BuildConfig.DEBUG && settings.mockApi()) {
                // activate this to use a mock
                return HttpUrl.parse("http://10.1.1.56:8888");
            } else {
                return HttpUrl.parse(UriHelper.of(context).base().toString());
            }
        };

        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(new OkHttpAwareConverterFactory(GsonConverterFactory.create(gson)))
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .client(this.client)
                .build()
                .create(Api.class);
    }

    private static class OkHttpAwareConverterFactory implements Converter.Factory {
        private final Converter.Factory factory;

        private OkHttpAwareConverterFactory(Converter.Factory factory) {
            this.factory = factory;
        }

        @Override
        public Converter<?> get(Type type) {
            if(type == ResponseBody.class || type == RequestBody.class) {
                return null;
            } else {
                return factory.get(type);
            }
        }
    }

    @Override
    public Api get() {
        return proxy;
    }

    private Api newProxyWrapper() {
        Api backend = newRestAdapter();
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
                int retryCount = 2;
                if (method.getAnnotation(GET.class) != null) {
                    return invokeWithRetry(backend, method, args,
                            ApiProvider::isHttpError, retryCount);
                } else {
                    return invokeWithRetry(backend, method, args,
                            ApiProvider::isHttpError, retryCount);
                }
            }

            try {
                return method.invoke(backend, args);
            } catch (InvocationTargetException targetError) {
                throw targetError.getCause();
            }
        });
    }

    @SuppressWarnings({"unchecked", "RedundantCast"})
    private static Observable<Object> invokeWithRetry(
            Api api, Method method, Object[] args,
            Predicate<Throwable> shouldRetryTest, int retryCount)

            throws IllegalAccessException, InvocationTargetException {

        Observable<Object> result = (Observable<Object>) method.invoke(api, args);
        for (int i = 0; i < retryCount; i++) {
            result = result.onErrorResumeNext(err -> {
                if (shouldRetryTest.apply(err)) {
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


    private static boolean isHttpError(Throwable error) {
        if (error instanceof HttpException) {
            HttpException httpError = (HttpException) error;
            String errorBody = "";
            try {
                Reader stream = httpError.response().errorBody().charStream();
                errorBody = CharStreams.toString(stream);
            } catch (IOException ignored) {
            }

            logger.warn("Got http error {} {}, with body: {}", httpError.code(),
                    httpError.message(), errorBody);

            return httpError.response().code() / 100 == 5;
        } else {
            return false;
        }
    }
}
