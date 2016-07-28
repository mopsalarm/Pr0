package com.pr0gramm.app.services;

import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.api.pr0gramm.LoginCookieHandler;
import com.pr0gramm.app.feed.ContentType;
import com.pr0gramm.app.orm.BenisRecord;
import com.pr0gramm.app.util.BackgroundScheduler;
import com.pr0gramm.app.util.Holder;

import org.immutables.value.Value;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Completable;
import rx.Observable;
import rx.functions.Func1;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;
import rx.util.async.Async;

import static com.pr0gramm.app.Settings.resetContentTypeSettings;
import static com.pr0gramm.app.util.AndroidUtility.checkNotMainThread;
import static org.joda.time.Duration.standardDays;
import static rx.functions.Actions.empty;

/**
 */
@Singleton
@org.immutables.gson.Gson.TypeAdapters
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger("UserService");

    private static final String KEY_LAST_LOF_OFFSET = "UserService.lastLogLength";
    private static final String KEY_LAST_USER_INFO = "UserService.lastUserInfo";
    private static final String KEY_LAST_LOGIN_STATE = "UserService.lastLoginState";

    private static final LoginState NOT_AUTHORIZED = ImmutableLoginState.builder()
            .id(-1).score(0).mark(0)
            .admin(false).premium(false)
            .authorized(false)
            .build();

    private final Object lock = new Object();

    private final Api api;
    private final VoteService voteService;
    private final SeenService seenService;
    private final InboxService inboxService;
    private final LoginCookieHandler cookieHandler;
    private final SharedPreferences preferences;
    private final Holder<SQLiteDatabase> database;

    private final Gson gson;
    private final Settings settings;

    private final AtomicBoolean fullSyncInProgress = new AtomicBoolean();

    // login state and observable for that.
    private LoginState loginState = NOT_AUTHORIZED;
    private final BehaviorSubject<LoginState> loginStateObservable = BehaviorSubject.create(loginState);

    @Inject
    public UserService(Api api,
                       VoteService voteService,
                       SeenService seenService, InboxService inboxService, LoginCookieHandler cookieHandler,
                       SharedPreferences preferences, Settings settings, Gson gson,
                       SingleShotService sso, Holder<SQLiteDatabase> database) {

        this.api = api;
        this.seenService = seenService;
        this.voteService = voteService;
        this.inboxService = inboxService;
        this.cookieHandler = cookieHandler;
        this.preferences = preferences;
        this.settings = settings;
        this.gson = gson;
        this.database = database;

        // only restore user data if authorized.
        if (cookieHandler.hasCookie()) {
            restoreLatestUserInfo();
        }

        this.cookieHandler.setOnCookieChangedListener(this::onCookieChanged);

        loginStateObservable.subscribe(this::persistLatestLoginState);

        // this is not nice, and will get removed in one or two versions!
        // TODO REMOVE THIS ASAP.
        loginStateObservable
                .filter(state -> state.authorized() && state.uniqueToken() == null)
                .switchMap(state -> api.identifier().subscribeOn(BackgroundScheduler.instance()))
                .map(Api.UserIdentifier::identifier)
                .onErrorResumeNext(Observable.empty())
                .subscribe(this::updateUniqueToken);

        loginStateObservable.subscribe(state -> {
            Track.updateAuthorizedState(state.authorized());
        });
    }

    private LoginState updateLoginState(Func1<ImmutableLoginState, LoginState> transformer) {
        synchronized (lock) {
            LoginState newLoginState = transformer.call(ImmutableLoginState.copyOf(this.loginState));

            // persist and publish
            if (newLoginState != loginState) {
                this.loginState = newLoginState;
                this.loginStateObservable.onNext(newLoginState);
            }

            return newLoginState;
        }
    }

    private LoginState updateLoginStateIfAuthorized(Func1<ImmutableLoginState, LoginState> transformer) {
        return updateLoginState(loginState -> {
            if (loginState.authorized()) {
                return transformer.call(loginState);
            } else {
                return loginState;
            }
        });
    }

    private void updateUniqueToken(@Nullable String uniqueToken) {
        if (uniqueToken == null)
            return;

        updateLoginStateIfAuthorized(loginState -> loginState.withUniqueToken(uniqueToken));
    }

    /**
     * Restore the latest user info from the shared preferences
     */
    private void restoreLatestUserInfo() {
        String lastLoginState = preferences.getString(KEY_LAST_LOGIN_STATE, null);
        String lastUserInfo = preferences.getString(KEY_LAST_USER_INFO, null);

        if (lastLoginState != null) {
            Async.start(() -> gson.fromJson(lastLoginState, LoginState.class), BackgroundScheduler.instance())
                    .onErrorResumeNext(Observable.empty())
                    .doOnNext(info -> logger.info("Restoring login state: {}", info))
                    .map(state -> ImmutableLoginState.copyOf(state).withBenisHistory(loadBenisHistory(state.id())))
                    .subscribe(
                            loginState -> updateLoginState(ignored -> loginState),
                            error -> logger.warn("Could not restore login state: " + error));

        } else if (lastUserInfo != null) {
            // TODO will be removed soon

            Async.start(() -> createLoginState(gson.fromJson(lastUserInfo, Api.Info.class)), BackgroundScheduler.instance())
                    .doOnNext(info -> logger.info("Restoring api user info: {}", info))
                    .filter(info -> cookieHandler.hasCookie())
                    .subscribe(
                            loginState -> updateLoginState(ignored -> loginState),
                            error -> logger.warn("Could not restore user info: " + error));
        }
    }

    private void onCookieChanged() {
        Optional<String> cookie = cookieHandler.getLoginCookie();
        if (!cookie.isPresent()) {
            logout();
        }
    }

    public Observable<LoginProgress> login(String username, String password) {
        return api.login(username, password).flatMap(login -> {
            List<Observable<?>> observables = new ArrayList<>();

            if (login.success()) {
                observables.add(updateCachedUserInfo()
                        .doOnTerminate(() -> updateUniqueToken(login.identifier().orNull()))
                        .toObservable());

                // perform initial sync in background.
                sync().subscribeOn(BackgroundScheduler.instance()).subscribe(
                        empty(),
                        err -> logger.error("Could not perform initial sync during login", err));
            }

            // wait for sync to complete before emitting login result.
            observables.add(Observable.just(new LoginProgress(login)));
            return Observable.concatDelayError(observables).ofType(LoginProgress.class);
        });
    }

    /**
     * Check if we can do authorized requests.
     */
    public boolean isAuthorized() {
        return cookieHandler.hasCookie() && loginState.authorized();
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
    public Completable logout() {
        return Async.<Void>start(() -> {
            updateLoginState(oldState -> NOT_AUTHORIZED);

            // removing cookie from requests
            cookieHandler.clearLoginCookie(false);

            // remove sync id
            preferences.edit()
                    .remove(KEY_LAST_LOF_OFFSET)
                    .remove(KEY_LAST_USER_INFO)
                    .remove(KEY_LAST_LOGIN_STATE)
                    .apply();

            // clear all the vote cache
            voteService.clear();

            // clear the seen items
            seenService.clear();

            // no more read messages.
            inboxService.forgetReadMessage();
            inboxService.publishUnreadMessagesCount(0);

            // and reset the content user, because only signed in users can
            // see the nsfw and nsfl stuff.
            resetContentTypeSettings(settings);

            return null;
        }, BackgroundScheduler.instance()).toCompletable();
    }

    public Observable<LoginState> loginState() {
        return loginStateObservable.asObservable();
    }

    /**
     * Performs a sync. This updates the vote cache with all the votes that
     * where performed since the last call to sync.
     */
    public Observable<Api.Sync> sync() {
        if (!cookieHandler.hasCookie())
            return Observable.empty();

        // tell the sync request where to start
        long lastLogOffset = preferences.getLong(KEY_LAST_LOF_OFFSET, 0L);
        boolean fullSync = lastLogOffset == 0;

        if (fullSync && !fullSyncInProgress.compareAndSet(false, true)) {
            // fail fast if full sync is in already in progress.
            return Observable.empty();
        }

        return api.sync(lastLogOffset).doAfterTerminate(() -> fullSyncInProgress.set(false)).flatMap(response -> {
            inboxService.publishUnreadMessagesCount(response.inboxCount());

            int userId = loginState.id();
            if (userId > 0) {
                // save the current benis value
                BenisRecord.storeValue(database.value(), userId, response.score());

                // and load the current benis history
                Graph scoreGraph = loadBenisHistory(userId);

                updateLoginStateIfAuthorized(loginState -> loginState
                        .withScore(response.score())
                        .withBenisHistory(scoreGraph));
            }

            try {
                voteService.applyVoteActions(response.log());

                // store syncId for next time.
                if (response.logLength() > lastLogOffset) {
                    preferences.edit()
                            .putLong(KEY_LAST_LOF_OFFSET, response.logLength())
                            .apply();
                }
            } catch (Throwable error) {
                return Observable.error(error);
            }

            return Observable.just(response);
        });
    }

    /**
     * Retrieves the user data and stores part of the data in the database.
     */
    public Observable<Api.Info> info(String username) {
        return api.info(username, null);
    }

    /**
     * Retrieves the user data and stores part of the data in the database.
     */
    public Observable<Api.Info> info(String username, Set<ContentType> contentTypes) {
        return api.info(username, ContentType.combine(contentTypes));
    }

    /**
     * Returns information for the current user, if a user is signed in.
     *
     * @return The info, if the user is currently signed in.
     */
    public Observable<Api.Info> info() {
        return Observable.from(getName().asSet()).flatMap(this::info);
    }

    /**
     * Update the cached user info in the background.
     */
    public Completable updateCachedUserInfo() {
        PublishSubject publishSubject = PublishSubject.create();

        info().retry(3)
                .subscribeOn(BackgroundScheduler.instance())
                .doOnNext(info -> {
                    LoginState loginState = createLoginState(info);
                    updateLoginState(old -> loginState);
                })
                .doOnTerminate(publishSubject::onCompleted)
                .subscribe(empty(), error -> logger.warn("Could not update user info.", error));

        return publishSubject.toCompletable();
    }

    /**
     * Persists the given login state to a preference storage.
     */
    private void persistLatestLoginState(LoginState state) {
        try {
            if (state.authorized()) {
                logger.info("persisting logins state now.");

                String encoded = gson.toJson(state);
                preferences.edit()
                        .putString(KEY_LAST_LOGIN_STATE, encoded)
                        .apply();
            } else {
                preferences.edit()
                        .remove(KEY_LAST_LOGIN_STATE)
                        .apply();
            }

        } catch (RuntimeException error) {
            logger.warn("Could not persist latest user info", error);
        }
    }

    private LoginState createLoginState(Api.Info info) {
        checkNotMainThread();

        Api.Info.User user = info.getUser();

        return ImmutableLoginState.builder()
                .authorized(true)
                .id(user.getId())
                .name(user.getName())
                .mark(user.getMark())
                .score(user.getScore())
                .premium(isPremiumUser())
                .admin(userIsAdmin())
                .benisHistory(loadBenisHistory(user.getId()))
                .build();
    }

    public boolean userIsAdmin() {
        return cookieHandler.getCookie().transform(this::isTruthValue).or(false);
    }

    private boolean isTruthValue(LoginCookieHandler.Cookie cookie) {
        String repr = String.valueOf(cookie.admin);
        return repr.startsWith("1") || "true".equalsIgnoreCase(repr);
    }

    private Graph loadBenisHistory(int userId) {
        Stopwatch watch = Stopwatch.createStarted();

        Duration historyLength = standardDays(7);
        Instant now = Instant.now();
        Instant start = now.minus(historyLength);

        // get the values and transform them
        ImmutableList<Graph.Point> points = FluentIterable
                .from(BenisRecord.findValuesLaterThan(database.value(), userId, start))
                .transform(record -> {
                    double x = record.time;
                    return new Graph.Point(x, record.benis);
                }).toList();

        logger.info("Loading benis graph took " + watch);
        return new Graph(start.getMillis(), now.getMillis(), points);
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


    /**
     * Returns an observable that produces the unique user token, if a hash is currently
     * available. This produces "null", if the user is currently not signed in.
     */
    public Observable<String> userToken() {
        return loginStateObservable.take(1).flatMap(value -> {
            if (value.authorized()) {
                return Observable.just(value.uniqueToken());
            } else {
                return Observable.just(null);
            }
        });
    }

    public Observable<Api.AccountInfo> accountInfo() {
        return api.accountInfo();
    }

    @Value.Immutable
    public interface LoginState {
        @Nullable
        @org.immutables.gson.Gson.Ignore
        Graph benisHistory();

        @Nullable
        String uniqueToken();

        @Nullable
        String name();

        int id();

        int score();

        int mark();

        boolean admin();

        boolean premium();

        boolean authorized();
    }

    public static final class LoginProgress {
        @Nullable
        private final Api.Login login;

        private final float progress;

        LoginProgress(float progress) {
            this.progress = progress;
            this.login = null;
        }

        LoginProgress(@Nullable Api.Login login) {
            this.progress = 1.f;
            this.login = login;
        }

        public float getProgress() {
            return progress;
        }

        public Optional<Api.Login> getLogin() {
            return Optional.fromNullable(login);
        }
    }
}
