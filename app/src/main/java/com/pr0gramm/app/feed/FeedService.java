package com.pr0gramm.app.feed;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.inject.Singleton;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.api.pr0gramm.response.Feed;
import com.pr0gramm.app.api.pr0gramm.response.Post;

import javax.inject.Inject;

import rx.Observable;

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
        return performRequest(query, start, Optional.<Long>absent());
    }

    public Observable<Feed> getFeedItemsNewer(Query query, long start) {
        return performRequest(query, Optional.<Long>absent(), Optional.of(start));
    }

    private Observable<Feed> performRequest(Query query, Optional<Long> older, Optional<Long> newer) {

        // value for the promoted field
        int promoted = (query.getFeedType() == FeedType.PROMOTED) ? 1 : 0;

        int flags = ContentType.combine(query.getContentTypes());
        String tags = query.getTags().orNull();
        String user = query.getUser().orNull();

        // FIXME this is quite hacky right now.
        String likes = query.getLikes().orNull();
        String self = Strings.isNullOrEmpty(likes) ? null : "true";

        return api.itemsGet(promoted, older.orNull(), newer.orNull(), flags, tags, likes, self, user);
    }

    public Observable<Post> loadPostDetails(long id) {
        return api.info(id);
    }
}
