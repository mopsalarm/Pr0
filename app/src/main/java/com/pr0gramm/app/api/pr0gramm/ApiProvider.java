package com.pr0gramm.app.api.pr0gramm;

import android.annotation.SuppressLint;
import android.content.Context;

import com.google.common.base.Predicate;
import com.google.common.io.CharStreams;
import com.google.common.reflect.Reflection;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.gson.Gson;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.Debug;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.UriHelper;
import com.pr0gramm.app.util.AndroidUtility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import retrofit2.BaseUrl;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.HttpException;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import rx.Observable;

/**
 */
@Singleton
public class ApiProvider implements Provider<Api> {
    private static final Logger logger = LoggerFactory.getLogger("ApiProvider");

    private final Api apiInstance;

    @Inject
    public ApiProvider(Context context, OkHttpClient client, LoginCookieHandler cookieHandler, Gson gson) {
        this.apiInstance = newProxyWrapper(newRestAdapter(context, client, gson), cookieHandler);
    }

    @Override
    public Api get() {
        return apiInstance;
    }

    private static Api newRestAdapter(Context context, OkHttpClient client, Gson gson) {
        Settings settings = Settings.of(context);

        BaseUrl baseUrl = () -> {
            if (BuildConfig.DEBUG && settings.mockApi()) {
                // activate this to use a mock
                return HttpUrl.parse("http://" + Debug.MOCK_API_HOST + ":8888");
            } else {
                return HttpUrl.parse(UriHelper.of(context).base().toString());
            }
        };

        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .client(client)
                .validateEagerly(true)
                .build()
                .create(Api.class);
    }

    private static Api newProxyWrapper(Api backend, LoginCookieHandler cookieHandler) {
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
                // only retry a get method, and only do it once.
                int retryCount = 1;
                if (method.getAnnotation(GET.class) != null) {
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
                try {
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
                } catch (Throwable error) {
                    // error while handling an error? oops!
                    return Observable.error(error);
                }
            });
        }

        return result;
    }


    @SuppressLint("NewApi")
    private static boolean isHttpError(Throwable error) {
        if (error instanceof HttpException) {
            HttpException httpError = (HttpException) error;
            String errorBody = "";
            try {
                try (Reader stream = httpError.response().errorBody().charStream()) {
                    errorBody = CharStreams.toString(stream);
                }
            } catch (IOException ignored) {
            }

            logger.warn("Got http error {} {}, with body: {}", httpError.code(),
                    httpError.message(), errorBody);

            return httpError.code() / 100 == 5;
        } else {
            return false;
        }
    }
}
