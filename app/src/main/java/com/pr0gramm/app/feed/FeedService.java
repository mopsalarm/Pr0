package com.pr0gramm.app.feed;

import com.google.common.base.Optional;
import com.google.inject.Singleton;
import com.pr0gramm.app.ContentType;
import com.pr0gramm.app.api.Api;
import com.pr0gramm.app.api.Feed;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import rx.Observable;
import rx.functions.Func4;

/**
 */
@Singleton
public class FeedService {
    private final Api api;

    @Inject
    public FeedService(Api api) {
        this.api = api;
    }

    public Observable<Feed> getFeedItems(Query query, Optional<Long> start) {
        // value of the "older" field. depends on the feed-type.
        long older = start.or((long) Integer.MAX_VALUE);
        return performRequest(api::itemsGetOlder, query, older);
    }

    public Observable<Feed> getFeedItemsNewer(Query query, long start) {
        return performRequest(api::itemsGetNewer, query, start);
    }

    private Observable<Feed> performRequest(
            Func4<Integer, Long, Integer, String, Observable<Feed>> function, Query query, long older) {

        // value for the promoted field
        int promoted = (query.getFeedType() == FeedType.PROMOTED) ? 1 : 0;

        int flags = ContentType.combine(query.getContentTypes());
        String tags = query.getTags().or("");

        return function.call(promoted, older, flags, tags);
    }
}
