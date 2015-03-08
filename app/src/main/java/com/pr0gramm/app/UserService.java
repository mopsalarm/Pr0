package com.pr0gramm.app;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.gson.Gson;
import com.pr0gramm.app.api.Api;
import com.pr0gramm.app.api.LoginResponse;
import com.pr0gramm.app.api.SyncResponse;

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
    private final Api api;
    private final VoteService voteService;
    private final LoginCookieHandler cookieHandler;
    private final Gson gson;

    private final BehaviorSubject<LoginState> loginStateObservable
            = BehaviorSubject.create(LoginState.NOT_AUTHORIZED);

    private long lastSyncId;

    @Inject
    public UserService(Api api, VoteService voteService, LoginCookieHandler cookieHandler, Gson gson) {
        this.api = api;
        this.gson = gson;
        this.voteService = voteService;

        this.cookieHandler = cookieHandler;
        this.cookieHandler.setOnCookieChangedListener(this::onCookieChanged);
    }

    private void onCookieChanged() {
        Optional<String> cookie = cookieHandler.getLoginCookie();
        LoginState state = cookie.isPresent() ? LoginState.AUTHORIZED : LoginState.NOT_AUTHORIZED;
        loginStateObservable.onNext(state);
    }

    public Observable<LoginResponse> login(String username, String password) {
        return api.login(username, password).map(response -> {
            if (response.isSuccess()) {
                // wait for the first sync to complete.
                SyncResponse syncResponse = sync().toBlocking().first();
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
            voteService.clear();
            cookieHandler.clearLoginCookie();
            return null;
        }, Schedulers.io()).ignoreElements();
    }

    public Observable<LoginState> getLoginStateObservable() {
        return loginStateObservable.asObservable();
    }

    /**
     * Performs a sync. Someone needs to subscribe to the returned observable
     */
    public Observable<SyncResponse> sync() {
        if (!isAuthorized())
            return Observable.empty();

        return api.sync(lastSyncId).map(response -> {
            // FIXME not threadsafe
            lastSyncId = response.getLastId();

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
        AUTHORIZED, NOT_AUTHORIZED;
    }
}
