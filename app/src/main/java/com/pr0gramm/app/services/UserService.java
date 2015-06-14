package com.pr0gramm.app.services;

import android.content.SharedPreferences;
import android.graphics.PointF;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.Graph;
import com.pr0gramm.app.LoginCookieHandler;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.api.pr0gramm.Info;
import com.pr0gramm.app.api.pr0gramm.response.Login;
import com.pr0gramm.app.api.pr0gramm.response.Sync;
import com.pr0gramm.app.orm.BenisRecord;

import org.joda.time.Duration;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;
import rx.util.async.Async;

import static com.pr0gramm.app.AndroidUtility.checkNotMainThread;
import static com.pr0gramm.app.orm.BenisRecord.getBenisValuesAfter;
import static org.joda.time.Duration.standardDays;

/**
 */
@Singleton
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private static final String KEY_LAST_SYNC_ID = "UserService.lastSyncId";

    private final Api api;
    private final VoteService voteService;
    private final SeenService seenService;
    private final LoginCookieHandler cookieHandler;
    private final SharedPreferences preferences;

    private final BehaviorSubject<LoginState> loginStateObservable
            = BehaviorSubject.create(LoginState.NOT_AUTHORIZED);

    private final Settings settings;

    @Inject
    public UserService(Api api,
                       VoteService voteService,
                       SeenService seenService, LoginCookieHandler cookieHandler,
                       SharedPreferences preferences, Settings settings) {

        this.api = api;
        this.seenService = seenService;
        this.voteService = voteService;
        this.cookieHandler = cookieHandler;
        this.preferences = preferences;
        this.settings = settings;

        this.cookieHandler.setOnCookieChangedListener(this::onCookieChanged);
    }

    private void onCookieChanged() {
        Optional<String> cookie = cookieHandler.getLoginCookie();
        if (!cookie.isPresent())
            logout();
    }

    public Observable<Login> login(String username, String password) {
        return api.login(username, password).flatMap(response -> {
            Observable<Login> result;
            if (response.isSuccess()) {
                // wait for the first sync to complete.
                result = sync().concatMap(ignored -> info())
                        .ignoreElements()
                        .cast(Login.class);

            } else {
                result = Observable.empty();
            }

            return result.concatWith(Observable.just(response));
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
            loginStateObservable.onNext(LoginState.NOT_AUTHORIZED);

            // removing cookie from requests
            cookieHandler.clearLoginCookie(false);

            // remove sync id
            preferences.edit().remove(KEY_LAST_SYNC_ID).apply();

            // clear all the vote cache
            voteService.clear();

            // clear the seen items
            seenService.clear();

            // and reset the content user, because only signed in users can
            // see the nsfw and nsfl stuff.
            Settings.resetContentTypeSettings(settings);

            return null;
        }, Schedulers.io()).ignoreElements();
    }

    public Observable<LoginState> getLoginStateObservable() {
        return loginStateObservable.asObservable();
    }

    /**
     * Performs a sync. This updates the vote cache with all the votes that
     * where performed since the last call to sync. This operation is blocking.
     * <p>
     * Someone needs to subscribe to the returned observable.
     */
    public Observable<Sync> sync() {
        checkNotMainThread();
        if (!isAuthorized())
            return Observable.empty();

        // tell the sync request where to start
        long lastSyncId = preferences.getLong(KEY_LAST_SYNC_ID, 0L);
        return api.sync(lastSyncId).map(response -> {
            // store syncId for next time.
            if (response.getLastId() > lastSyncId) {
                preferences.edit()
                        .putLong(KEY_LAST_SYNC_ID, response.getLastId())
                        .apply();
            }

            // and apply votes now
            voteService.applyVoteActions(response.getLog());
            return response;
        });
    }

    /**
     * Retrieves the user data and stores part of the data in the database.
     */
    public Observable<Info> info(String username) {
        return api.info(username);
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
            return info;
        });
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
        Instant start = Instant.now().minus(historyLength);

        // get the values and transform them
        ImmutableList<PointF> points = FluentIterable
                .from(getBenisValuesAfter(userId, start))
                .transform(record -> {
                    float x = record.getTimeMillis() - start.getMillis();
                    return new PointF(x, record.getBenis());
                }).toList();

        logger.info("Loading benis graph took " + watch);
        return new Graph(0, historyLength.getMillis(), points);
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

    public static class LoginState {
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
}
