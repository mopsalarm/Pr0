package com.pr0gramm.app.services;

import android.content.SharedPreferences;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.gson.Gson;
import com.pr0gramm.app.AndroidUtility;
import com.pr0gramm.app.LoginCookieHandler;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.api.pr0gramm.response.Login;
import com.pr0gramm.app.api.pr0gramm.response.Sync;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;
import rx.util.async.Async;

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
        LoginState state = cookie.isPresent() ? LoginState.AUTHORIZED : LoginState.NOT_AUTHORIZED;
        loginStateObservable.onNext(state);
    }

    public Observable<Login> login(String username, String password) {
        return api.login(username, password).map(response -> {
            if (response.isSuccess()) {
                // wait for the first sync to complete.
                Sync syncResponse = sync().toBlocking().first();
            }

            return response;
        });
    }

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

            return null;
        }, Schedulers.io()).ignoreElements();
    }

    public Observable<LoginState> getLoginStateObservable() {
        return loginStateObservable.asObservable();
    }

    /**
     * Performs a sync. This updates the vote cache with all the votes that
     * where performed since the last call to sync.
     * <p>
     * Someone needs to subscribe to the returned observable.
     */
    public Observable<Sync> sync() {
        if (!isAuthorized())
            return Observable.empty();

        long lastSyncId = preferences.getLong(KEY_LAST_SYNC_ID, 0L);
        return api.sync(lastSyncId).map(response -> {
            if (response.getLastId() > lastSyncId) {
                // store syncId for next time.
                preferences.edit().putLong(KEY_LAST_SYNC_ID, response.getLastId()).apply();
            }

            // and apply votes now
            voteService.applyVoteActions(response.getLog());
            return response;
        });
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

    public enum LoginState {
        AUTHORIZED, NOT_AUTHORIZED
    }
}
