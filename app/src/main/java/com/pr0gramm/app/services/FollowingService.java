package com.pr0gramm.app.services;

import com.google.common.collect.Sets;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.feed.Nothing;

import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.subjects.PublishSubject;

/**
 */
@Singleton
public class FollowingService {
    private final Api api;
    private final Set<String> following = Sets.newConcurrentHashSet();
    private final PublishSubject<String> changes = PublishSubject.create();

    @Inject
    public FollowingService(Api api) {
        this.api = api;
    }

    public Observable<Nothing> follow(String username) {
        return api.profileFollow(null, username).doOnCompleted(() -> {
            markAsFollowing(username, true);
        }).share();
    }

    public Observable<Nothing> unfollow(String username) {
        return api.profileUnfollow(null, username).doOnCompleted(() -> {
            markAsFollowing(username, false);
        }).share();
    }

    public void markAsFollowing(String username, boolean following) {
        boolean changed;
        if (following) {
            changed = this.following.add(username.toLowerCase());
        } else {
            changed = this.following.remove(username.toLowerCase());
        }

        if (changed) {
            changes.onNext(username.toLowerCase());
        }
    }

    public boolean isFollowing(String username) {
        return following.contains(username.toLowerCase());
    }

    public Observable<String> changes() {
        return changes.asObservable();
    }
}
