package com.pr0gramm.app.services;

import android.content.SharedPreferences;
import android.text.method.DateTimeKeyListener;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.gson.Gson;
import com.orm.SugarRecord;
import com.pr0gramm.app.AndroidUtility;
import com.pr0gramm.app.LoginCookieHandler;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.api.pr0gramm.Info;
import com.pr0gramm.app.api.pr0gramm.response.Login;
import com.pr0gramm.app.api.pr0gramm.response.Sync;
import com.pr0gramm.app.orm.BenisRecord;

import org.joda.time.Instant;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;
import rx.util.async.Async;

import static com.orm.SugarRecord.deleteAll;
import static com.pr0gramm.app.AndroidUtility.checkNotMainThread;

/**
 */
@Singleton
public class UserService {
    private static final String KEY_LAST_SYNC_ID = "UserService.lastSyncId";

    private final Api api;
    private final VoteService voteService;
    private final LoginCookieHandler cookieHandler;
    private final Gson gson;
    private final SharedPreferences preferences;

    private final BehaviorSubject<LoginState> loginStateObservable
            = BehaviorSubject.create(LoginState.NOT_AUTHORIZED);

    @Inject
    public UserService(Api api,
                       VoteService voteService,
                       LoginCookieHandler cookieHandler,
                       Gson gson,
                       SharedPreferences preferences) {

        this.api = api;
        this.gson = gson;
        this.voteService = voteService;
        this.cookieHandler = cookieHandler;
        this.preferences = preferences;

        this.cookieHandler.setOnCookieChangedListener(this::onCookieChanged);
    }

    private void onCookieChanged() {
        Optional<String> cookie = cookieHandler.getLoginCookie();
        if (!cookie.isPresent())
            loginStateObservable.onNext(LoginState.NOT_AUTHORIZED);
    }

    public Observable<Login> login(String username, String password) {
        return api.login(username, password).map(response -> {
            if (response.isSuccess()) {
                // wait for the first sync to complete.
                sync();

                // get info about the current user
                Optional<Info> info = info();
                if (!info.isPresent())
                    throw new IllegalStateException("Login successful, but info could not be loaded");

                loginStateObservable.onNext(new LoginState(info.get()));
            }

            return response;
        });
    }

    /**
     * Check if we can do authorized requests.
     */
    public boolean isAuthorized() {
        return cookieHandler.getLoginCookie().isPresent();
    }

    /**
     * Performs a logout of the user.
     */
    public Observable<Void> logout() {
        return Async.<Void>start(() -> {
            // removing cookie from requests
            cookieHandler.clearLoginCookie();

            // remove sync id
            preferences.edit().remove(KEY_LAST_SYNC_ID).apply();

            // clear all the vote cache
            voteService.clear();

            // remove all benis records
            deleteAll(BenisRecord.class);

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
    public Optional<Sync> sync() {
        checkNotMainThread();
        if (!isAuthorized())
            return Optional.absent();

        // tell the sync request where to start
        long lastSyncId = preferences.getLong(KEY_LAST_SYNC_ID, 0L);
        Sync response = api.sync(lastSyncId);

        // store syncId for next time.
        if (response.getLastId() > lastSyncId) {
            preferences.edit()
                    .putLong(KEY_LAST_SYNC_ID, response.getLastId())
                    .apply();
        }

        // and apply votes now
        voteService.applyVoteActions(response.getLog());
        return Optional.of(response);
    }

    /**
     * Retrieves the user data and stores part of the data in the database.
     */
    public Info info(String username) {
        checkNotMainThread();
        return api.info(username);
    }

    /**
     * Returns information for the current user, if a user is signed in.
     * As a side-effect, this will store the current benis of the user in the database.
     *
     * @return The info, if the user is currently signed in.
     */
    public Optional<Info> info() {
        checkNotMainThread();

        Optional<Info> info = getName().transform(this::info);
        if (info.isPresent()) {
            Info.User user = info.get().getUser();

            // stores the current benis value
            BenisRecord record = new BenisRecord(Instant.now(), user.getScore());
            record.save();

            // updates the login state and ui
            loginStateObservable.onNext(new LoginState(info.get()));
        }

        return info;
    }

    /**
     * Gets the name of the current user from the cookie. This will only
     * work, if the user is authorized.
     *
     * @return The name of the currently signed in user.
     */
    public Optional<String> getName() {
        return cookieHandler.getLoginCookie().transform(value -> {
            value = AndroidUtility.urlDecode(value, Charsets.UTF_8);
            Cookie cookie = gson.fromJson(value, Cookie.class);
            return cookie.n;
        });
    }

    private static class Cookie {
        public String n;
    }

    public static class LoginState {
        private final Info info;

        private LoginState() {
            this(null);
        }

        private LoginState(Info info) {
            this.info = info;
        }

        public boolean isAuthorized() {
            return info != null;
        }

        public Info getInfo() {
            return info;
        }

        public static final LoginState NOT_AUTHORIZED = new LoginState();
    }
}
