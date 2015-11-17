package com.pr0gramm.app.services;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.Debug;
import com.pr0gramm.app.util.AndroidUtility;
import com.pr0gramm.app.util.scripting.Interpreter;
import com.squareup.okhttp.OkHttpClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit.GsonConverterFactory;
import retrofit.Retrofit;
import retrofit.RxJavaCallAdapterFactory;
import retrofit.http.GET;
import retrofit.http.Url;
import rx.Observable;

/**
 * Important messages from Mopsalarm!
 * Mostly just for fun
 */
@Singleton
public class ImportantMessageService {
    private static final Logger logger = LoggerFactory.getLogger(ImportantMessageService.class);

    private final Interpreter interpreter;
    private final SingleShotService singleShotService;
    private final ApiInterface apiInterface;
    private final UserService userService;

    @Inject
    public ImportantMessageService(Gson gson, OkHttpClient httpClient,
                                   SingleShotService singleShotService, UserService userService) {

        this.singleShotService = singleShotService;
        this.userService = userService;

        this.interpreter = new Interpreter.Builder()
                .func("is-first-time", String.class, this::funcIsFirstTime)
                .func("is-first-time-today", String.class, this::funcIsFirstTimeToday)
                .func("is-time-millis-after", Long.class, this::funcIsTimeMillisAfter)
                .func("is-time-millis-before", Long.class, this::funcIsTimeMillisBefore)
                .build();

        apiInterface = new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .client(httpClient)
                .baseUrl("http://127.0.0.1")
                .build()
                .create(ApiInterface.class);
    }

    /**
     * Returns an observable producing messages that are to be shown right now.
     */
    public Observable<MessageDefinition> messages() {
        return userService.loginState()
                .take(1)
                .flatMap(loginState -> Observable
                        .from(urls())
                        .flatMap(url -> apiInterface.get(url)
                                .doOnError(error -> logger.error("Error while fetching messages: " + error))
                                .onErrorResumeNext(Observable.empty()))
                        .flatMap(Observable::from)
                        .filter(def -> isValidMessageDefinition(loginState, def)));
    }

    private Interpreter.Scope scope(UserService.LoginState loginState) {
        return Interpreter.Scope.of(ImmutableMap.<String, Object>builder()
                .put("version", BuildConfig.VERSION_CODE)
                .put("flavor", BuildConfig.FLAVOR)
                .put("is-play-store", BuildConfig.IS_PLAYSTORE_RELEASE)
                .put("debug", BuildConfig.DEBUG)
                .put("requires-unlock-plugin", BuildConfig.REQUIRES_UNLOCK_PLUGIN)
                .put("is-logged-in", loginState.isAuthorized())
                .put("is-premium-user", userService.isPremiumUser())
                .put("user-class", varGetUserClass(loginState))
                .build());
    }

    private boolean isValidMessageDefinition(UserService.LoginState loginState, MessageDefinition definition) {
        try {
            Interpreter.Scope scope = scope(loginState);
            Object result = interpreter.evaluate(scope, definition.condition());
            return (boolean) result;

        } catch (Exception error) {
            AndroidUtility.logToCrashlytics(error);
            return false;
        }
    }

    private List<String> urls() {
        ImmutableList.Builder<String> builder = ImmutableList.<String>builder()
                .add("https://github.com/mopsalarm/pr0gramm-updates/raw/messages.json")
                .add("http://pr0.wibbly-wobbly.de/messages.json");

        if (BuildConfig.DEBUG) {
            builder.add("http://" + Debug.MOCK_API_HOST + "/messages.json");
        }

        return builder.build();
    }

    private Object varGetUserClass(UserService.LoginState loginState) {
        if (loginState != null) {
            return loginState.getInfo().getUser().getMark();
        } else {
            return -1;
        }
    }

    private Object funcIsFirstTime(String key) {
        return singleShotService.isFirstTime(key);
    }

    private Object funcIsFirstTimeToday(String key) {
        return singleShotService.isFirstTimeToday(key);
    }

    private Object funcIsTimeMillisAfter(long deadline) {
        return System.currentTimeMillis() > deadline;
    }

    private Object funcIsTimeMillisBefore(long deadline) {
        return System.currentTimeMillis() < deadline;
    }

    private interface ApiInterface {
        @GET
        Observable<List<MessageDefinition>> get(@Url String url);
    }
}
