package com.pr0gramm.app.services;

import android.util.Patterns;

import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.api.pr0gramm.response.AccountInfo;

import org.immutables.value.Value;

import java.util.List;
import java.util.regex.Matcher;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;

/**
 */
@Singleton
public class InviteService {
    private final Api api;

    @Inject
    public InviteService(Api api) {
        this.api = api;
    }

    public Observable<Void> send(String email) {
        Matcher matcher = Patterns.EMAIL_ADDRESS.matcher(email);
        if (!matcher.matches())
            return Observable.error(new InviteException("email"));

        return api.invite(null, email).flatMap(response -> {
            if (response.error() != null) {
                return Observable.error(new InviteException(response.error()));
            } else {
                return Observable.just(null);
            }
        });
    }

    public Observable<Invites> invites() {
        return api.accountInfo().map(info -> ImmutableInvites.builder()
                .inviteCount(info.account().invites())
                .invited(info.invited())
                .build());
    }

    public static class InviteException extends RuntimeException {
        private final String errorCode;

        public InviteException(String errorCode) {
            this.errorCode = errorCode;
        }

        public boolean noMoreInvites() {
            return ERROR_INVITES.equalsIgnoreCase(errorCode);
        }

        public boolean emailFormat() {
            return ERROR_EMAIL_FORMAT.equalsIgnoreCase(errorCode);
        }

        public boolean emailInUse() {
            return ERROR_IN_USE.equalsIgnoreCase(errorCode);
        }
    }

    @Value.Immutable
    public interface Invites {
        int inviteCount();

        List<AccountInfo.Invite> invited();
    }

    public static final String ERROR_EMAIL_FORMAT = "emailInvalid";
    public static final String ERROR_INVITES = "noInvites";
    public static final String ERROR_IN_USE = "emailInUse";
}
