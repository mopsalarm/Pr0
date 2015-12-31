package com.pr0gramm.app.services;

import android.content.SharedPreferences;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.orm.SugarTransactionHelper;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.api.meta.MetaService;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.api.pr0gramm.LoginCookieHandler;
import com.pr0gramm.app.api.pr0gramm.response.AccountInfo;
import com.pr0gramm.app.api.pr0gramm.response.Info;
import com.pr0gramm.app.api.pr0gramm.response.Login;
import com.pr0gramm.app.api.pr0gramm.response.Sync;
import com.pr0gramm.app.feed.ContentType;
import com.pr0gramm.app.orm.BenisRecord;
import com.pr0gramm.app.util.BackgroundScheduler;

import org.joda.time.Duration;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.subjects.BehaviorSubject;
import rx.util.async.Async;

import static com.pr0gramm.app.Settings.resetContentTypeSettings;
import static com.pr0gramm.app.orm.BenisRecord.getBenisValuesAfter;
import static com.pr0gramm.app.services.UserService.LoginState.NOT_AUTHORIZED;
import static com.pr0gramm.app.util.AndroidUtility.checkNotMainThread;
import static org.joda.time.Duration.standardDays;

/**
 */
@Singleton
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger("UserService");

    private static final String KEY_LAST_SYNC_ID = "UserService.lastSyncId";
    private static final String KEY_LAST_USER_INFO = "UserService.lastUserInfo";

    private final Api api;
    private final VoteService voteService;
    private final SeenService seenService;
    private final InboxService inboxService;
    private final LoginCookieHandler cookieHandler;
    private final SharedPreferences preferences;
    private final MetaService metaService;

    private final Gson gson;
    private final BehaviorSubject<LoginState> loginStateObservable = BehaviorSubject.create(NOT_AUTHORIZED);
    private final Settings settings;

    @Inject
    public UserService(Api api,
                       VoteService voteService,
                       SeenService seenService, InboxService inboxService, LoginCookieHandler cookieHandler,
                       SharedPreferences preferences, MetaService metaService, Settings settings, Gson gson) {

        this.api = api;
        this.seenService = seenService;
        this.voteService = voteService;
        this.inboxService = inboxService;
        this.cookieHandler = cookieHandler;
        this.preferences = preferences;
        this.metaService = metaService;
        this.settings = settings;
        this.gson = gson;

        restoreLatestUserInfo();
        this.cookieHandler.setOnCookieChangedListener(this::onCookieChanged);
    }

    /**
     * Restore the latest user info from the shared preferences
     */
    private void restoreLatestUserInfo() {
        Observable.just(preferences.getString(KEY_LAST_USER_INFO, null))
                .filter(value -> value != null)
                .map(encoded -> createLoginState(Optional.of(gson.fromJson(encoded, Info.class))))
                .subscribeOn(BackgroundScheduler.instance())
                .doOnNext(info -> logger.info("Restoring user info: {}", info))
                .filter(info -> isAuthorized())
                .subscribe(
                        loginStateObservable::onNext,
                        error -> logger.warn("Could not restore user info: " + error));
    }

    private void onCookieChanged() {
        Optional<String> cookie = cookieHandler.getLoginCookie();
        if (!cookie.isPresent())
            logout();
    }

    public Observable<LoginProgress> login(String username, String password) {
        return api.login(username, password).flatMap(login -> {
            Observable<LoginProgress> syncState;
            if (login.isSuccess()) {
                // perform initial sync.
                syncState = syncWithProgress()
                        .filter(val -> val instanceof Float)
                        .cast(Float.class)
                        .map(LoginProgress::new)
                        .mergeWith(info()
                                .flatMap(this::initializeBenisGraphUsingMetaService)
                                .ignoreElements()
                                .cast(LoginProgress.class))
                        .onErrorResumeNext(Observable.<LoginProgress>empty());

            } else {
                syncState = Observable.empty();
            }

            // wait for sync to complete before emitting login result.
            return syncState.concatWith(Observable.just(new LoginProgress(login)));
        });
    }

    /**
     * Check if we can do authorized requests.
     */
    public boolean isAuthorized() {
        return cookieHandler.hasCookie();
    }

    /**
     * Checks if the user has paid for a pr0mium account
     */
    public boolean isPremiumUser() {
        return cookieHandler.isPaid();
    }

    /**
     * Performs a logout of the user.
     */
    public Observable<Void> logout() {
        return Async.<Void>start(() -> {
            loginStateObservable.onNext(NOT_AUTHORIZED);

            // removing cookie from requests
            cookieHandler.clearLoginCookie(false);

            // remove sync id
            preferences.edit()
                    .remove(KEY_LAST_SYNC_ID)
                    .remove(KEY_LAST_USER_INFO)
                    .apply();

            // clear all the vote cache
            voteService.clear();

            // clear the seen items
            seenService.clear();

            // and reset the content user, because only signed in users can
            // see the nsfw and nsfl stuff.
            resetContentTypeSettings(settings);

            return null;
        }, BackgroundScheduler.instance()).ignoreElements();
    }

    public Observable<LoginState> loginState() {
        return loginStateObservable.asObservable();
    }

    /**
     * Performs a sync. This updates the vote cache with all the votes that
     * where performed since the last call to sync.
     */
    public Observable<Sync> sync() {
        return syncWithProgress().filter(val -> val instanceof Sync).cast(Sync.class);
    }

    /**
     * Performs a sync. If values need to be stored in the database, a sequence
     * of float values between 0 and 1 will be emitted. At the end, the sync
     * object is appended to the stream of values.
     */
    public Observable<Object> syncWithProgress() {
        if (!isAuthorized())
            return Observable.empty();

        BehaviorSubject<Float> progressSubject = BehaviorSubject.create();

        // tell the sync request where to start
        long lastSyncId = preferences.getLong(KEY_LAST_SYNC_ID, 0L);

        Observable<Sync> sync = api.sync(lastSyncId).map(response -> {
            inboxService.publishUnreadMessagesCount(response.getInboxCount());

            // store syncId for next time.
            if (response.getLastId() > lastSyncId) {
                preferences.edit()
                        .putLong(KEY_LAST_SYNC_ID, response.getLastId())
                        .apply();
            }

            // and apply votes now
            voteService.applyVoteActions(response.getLog(), progressSubject::onNext);
            progressSubject.onCompleted();

            return response;
        }).finallyDo(progressSubject::onCompleted);

        return sync.cast(Object.class).mergeWith(progressSubject.throttleFirst(50, TimeUnit.MILLISECONDS));
    }

    /**
     * Retrieves the user data and stores part of the data in the database.
     */
    public Observable<Info> info(String username) {
        return api.info(username, null);
    }

    /**
     * Retrieves the user data and stores part of the data in the database.
     */
    public Observable<Info> info(String username, Set<ContentType> contentTypes) {
        return api.info(username, ContentType.combine(contentTypes));
    }

    /**
     * Returns information for the current user, if a user is signed in.
     * As a side-effect, this will store the current benis of the user in the database.
     *
     * @return The info, if the user is currently signed in.
     */
    public Observable<Info> info() {
        return Observable.from(getName().asSet()).flatMap(this::info).map(info -> {
            Info.User user = info.getUser();

            // stores the current benis value
            BenisRecord record = new BenisRecord(user.getId(), Instant.now(), user.getScore());
            record.save();

            // updates the login state and ui
            loginStateObservable.onNext(createLoginState(Optional.of(info)));

            // and store the login state for later
            persistLatestUserInfo(info);

            return info;
        });
    }

    private void persistLatestUserInfo(Info info) {
        try {
            String encoded = gson.toJson(info);
            preferences.edit()
                    .putString(KEY_LAST_USER_INFO, encoded)
                    .apply();

        } catch (RuntimeException error) {
            logger.warn("Could not persist latest user info", error);
        }
    }

    private LoginState createLoginState(Optional<Info> info) {
        checkNotMainThread();

        int userId = info.get().getUser().getId();
        Graph benisHistory = loadBenisHistory(userId);
        return new LoginState(info.get(), benisHistory);
    }

    private Graph loadBenisHistory(int userId) {
        Stopwatch watch = Stopwatch.createStarted();

        Duration historyLength = standardDays(7);
        Instant now = Instant.now();
        Instant start = now.minus(historyLength);

        // get the values and transform them
        ImmutableList<Graph.Point> points = FluentIterable
                .from(getBenisValuesAfter(userId, start))
                .transform(record -> {
                    double x = record.getTimeMillis();
                    return new Graph.Point(x, record.getBenis());
                }).toList();

        logger.info("Loading benis graph took " + watch);
        return new Graph(start.getMillis(), now.getMillis(), points);
    }

    private Observable<Void> initializeBenisGraphUsingMetaService(Info userInfo) {
        Info.User user = userInfo.getUser();

        return metaService.getBenisHistory(user.getName())
                .doOnNext(graph -> {
                    SugarTransactionHelper.doInTransaction(() -> {
                        for (Graph.Point points : graph.points()) {
                            Instant time = new Instant((long) (points.x * 1000L));
                            int benisValue = (int) points.y;
                            new BenisRecord(user.getId(), time, benisValue).save();
                        }
                    });

                    loginStateObservable.onNext(createLoginState(Optional.of(userInfo)));
                })
                .ignoreElements()
                .cast(Void.class);
    }

    /**
     * Gets the name of the current user from the cookie. This will only
     * work, if the user is authorized.
     *
     * @return The name of the currently signed in user.
     */
    public Optional<String> getName() {
        return cookieHandler.getCookie().transform(cookie -> cookie.n);
    }

    public static Optional<Info.User> getUser(LoginState loginState) {
        if (loginState.getInfo() == null)
            return Optional.absent();

        return Optional.fromNullable(loginState.getInfo().getUser());
    }

    public Observable<AccountInfo> accountInfo() {
        return api.accountInfo();
    }

    public static final class LoginState {
        private final Info info;
        private final Graph benisHistory;

        private LoginState() {
            this(null, null);
        }

        private LoginState(Info info, Graph benisHistory) {
            this.info = info;
            this.benisHistory = benisHistory;
        }

        public boolean isAuthorized() {
            return info != null;
        }

        public Info getInfo() {
            return info;
        }

        public Graph getBenisHistory() {
            return benisHistory;
        }

        public static final LoginState NOT_AUTHORIZED = new LoginState();
    }

    public static final class LoginProgress {
        private final Optional<Login> login;
        private final float progress;

        public LoginProgress(float progress) {
            this.progress = progress;
            this.login = Optional.absent();
        }

        public LoginProgress(Login login) {
            this.progress = 1.f;
            this.login = Optional.of(login);
        }

        public float getProgress() {
            return progress;
        }

        public Optional<Login> getLogin() {
            return login;
        }
    }
}
