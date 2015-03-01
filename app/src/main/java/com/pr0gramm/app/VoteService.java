package com.pr0gramm.app;

import com.pr0gramm.app.api.Api;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.Nothing;
import com.pr0gramm.app.feed.Vote;

import javax.inject.Inject;

import rx.Observable;

/**
 */
public class VoteService {
    private final Api api;

    @Inject
    public VoteService(Api api) {
        this.api = api;
    }

    public Observable<Nothing> vote(FeedItem item, Vote vote) {
        return api.vote(item.getId(), vote.getVoteValue());
    }
}
