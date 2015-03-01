package com.pr0gramm.app;

import com.pr0gramm.app.api.Api;
import com.pr0gramm.app.api.LoginResponse;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;

/**
 */
@Singleton
public class LoginService {
    private final Api api;
    private final LoginCookieHandler cookieHandler;

    @Inject
    public LoginService(Api api, LoginCookieHandler cookieHandler) {
        this.api = api;
        this.cookieHandler = cookieHandler;
    }

    Observable<LoginResponse> login(String username, String password) {
        return api.login(username, password);
    }

    public boolean isAuthorized() {
        return cookieHandler.getLoginCookie().isPresent();
    }
}
