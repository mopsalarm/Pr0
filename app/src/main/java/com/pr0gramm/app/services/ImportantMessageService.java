package com.pr0gramm.app.services;

import android.app.Notification;
import android.content.Context;

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
    private static final Logger logger = LoggerFactory.getLogger("ImportantMessageService");

    private final SingleShotService singleShotService;
    private final ApiInterface apiInterface;
    private final UserService userService;
    private final NotificationService notificationService;

    @Inject
    public ImportantMessageService(Gson gson, OkHttpClient httpClient,
                                   SingleShotService singleShotService, UserService userService,
                                   NotificationService notificationService) {

        this.singleShotService = singleShotService;
        this.userService = userService;
        this.notificationService = notificationService;

        apiInterface = new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .client(httpClient)
                .baseUrl("http://127.0.0.1")
                .build()
                .create(ApiInterface.class);
    }

    private Interpreter newInterpreter(boolean testing) {
        return new Interpreter.Builder()
                .func("is-first-time-by-time-pattern", String.class,
                        (key, pattern) -> funcIsFirstTimeByTimePattern(testing, key, pattern))

                .func("is-first-time", String.class, key -> funcIsFirstTime(testing, key))
                .func("is-first-time-today", String.class, key -> funcIsFirstTimeToday(testing, key))
                .func("is-time-millis-after", Long.class, this::funcIsTimeMillisAfter)
                .func("is-time-millis-before", Long.class, this::funcIsTimeMillisBefore)
                .build();
    }

    /**
     * Returns an observable producing messages that are to be shown right now.
     */
    public Observable<MessageDefinition> messages() {
        if (!singleShotService.firstTimeInHour("check-important-messages"))
            return Observable.empty();

        return userService.loginState()
                .take(1)
                .flatMap(loginState -> Observable
                        .from(urls())
                        .flatMap(url -> apiInterface.get(url + "?_t=" + System.currentTimeMillis())
                                .doOnNext(list -> logger.info("Got {} messages from {}", list.size(), url))
                                .doOnError(error -> logger.warn("Error while fetching messages from {}: {}", url, error))
                                .onErrorResumeNext(Observable.empty()))
                        .flatMap(Observable::from)
                        .filter(def -> {
                            boolean valid = isValidMessageDefinition(loginState, def);
                            logger.info("Message {} ({}) {}", def.uniqueId(), def.title(),
                                    valid ? "accepted" : "rejected");

                            return valid;
                        }));
    }

    private Interpreter.Scope scope(UserService.LoginState loginState, MessageDefinition message) {
        return Interpreter.Scope.of(ImmutableMap.<String, Object>builder()
                .put("version", BuildConfig.VERSION_CODE)
                .put("flavor", BuildConfig.FLAVOR)
                .put("is-play-store", BuildConfig.IS_PLAYSTORE_RELEASE)
                .put("debug", BuildConfig.DEBUG)
                .put("requires-unlock-plugin", BuildConfig.REQUIRES_UNLOCK_PLUGIN)
                .put("is-logged-in", loginState.isAuthorized())
                .put("is-premium-user", userService.isPremiumUser())
                .put("user-class", varGetUserClass(loginState))
                .put("user-benis", varGetUserBenis(loginState))
                .build());
    }

    /**
     * Gets the message key for a message. It is ImportantMessage-[uniqueId]
     */
    public static String messageKey(MessageDefinition message) {
        return "ImportantMessageShown-" + message.uniqueId();
    }

    public void present(Context context, MessageDefinition message) {
        if (message.notification()) {
            Notification notification = message.asNotification(context);
            notificationService.showImportantMessage(message.uniqueId(), notification);
        } else {
            message.asDialog(context).show();
        }
    }

    private boolean isValidMessageDefinition(UserService.LoginState loginState, MessageDefinition message) {
        try {
            Interpreter.Scope scope = scope(loginState, message);
            Object result = newInterpreter(true).evaluate(scope, message.condition());
            return (boolean) result;

        } catch (Exception error) {
            AndroidUtility.logToCrashlytics(error);
            return false;
        }
    }

    public void messageAcknowledged(MessageDefinition message) {
        try {
            // we just evaluate the message again, but this time we apply all the
            // testings
            Interpreter.Scope scope = scope(UserService.LoginState.NOT_AUTHORIZED, message);
            newInterpreter(false).evaluate(scope, message.condition());

        } catch (Exception error) {
            AndroidUtility.logToCrashlytics(error);
        }
    }

    private List<String> urls() {
        // we use multiple api endpoints to fetch messages
        // you know, redundancy and stuff :)
        ImmutableList.Builder<String> builder = ImmutableList.<String>builder()
                .add("https://raw.githubusercontent.com/mopsalarm/pr0gramm-updates/master/messages.json")
                .add("https://mopsalarm.github.io/Pr0/messages.json")
                .add("https://pr0.wibbly-wobbly.de/messages.json");

        if (BuildConfig.DEBUG) {
            builder.add("http://" + Debug.MOCK_API_HOST + "/messages.json");
        }

        return builder.build();
    }

    private Object varGetUserClass(UserService.LoginState loginState) {
        return loginState.getInfo() != null ? loginState.getInfo().getUser().getMark() : -1;
    }

    private long varGetUserBenis(UserService.LoginState loginState) {
        return loginState.getInfo() != null ? loginState.getInfo().getUser().getScore() : -1;
    }

    private boolean funcIsFirstTime(boolean testOnly, String key) {
        if (testOnly) {
            return singleShotService.test().isFirstTime(key);
        } else {
            return singleShotService.isFirstTime(key);
        }
    }

    private boolean funcIsFirstTimeToday(boolean testOnly, String key) {
        if (testOnly) {
            return singleShotService.test().firstTimeToday(key);
        } else {
            return singleShotService.firstTimeToday(key);
        }
    }

    private boolean funcIsFirstTimeByTimePattern(boolean testOnly, String key, String pattern) {
        if (testOnly) {
            return singleShotService.test().firstTimeByTimePattern(key, pattern);
        } else {
            return singleShotService.firstTimeByTimePattern(key, pattern);
        }
    }

    private boolean funcIsTimeMillisAfter(long deadline) {
        return System.currentTimeMillis() > deadline;
    }

    private boolean funcIsTimeMillisBefore(long deadline) {
        return System.currentTimeMillis() < deadline;
    }

    private interface ApiInterface {
        @GET
        Observable<List<MessageDefinition>> get(@Url String url);
    }
}
