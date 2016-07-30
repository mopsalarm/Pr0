package com.pr0gramm.app.services;

import com.pr0gramm.app.api.pr0gramm.Api;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Completable;

/**
 */
@Singleton
public class ContactService {
    private final Api api;

    @Inject
    public ContactService(Api api) {
        this.api = api;
    }

    public Completable contactFeedback(String email, String subject, String message) {
        return api.contactSend(subject, email, message).toCompletable();
    }
}
