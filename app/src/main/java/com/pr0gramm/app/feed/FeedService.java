package com.pr0gramm.app.feed;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.inject.Singleton;
import com.pr0gramm.app.ContentType;
import com.pr0gramm.app.api.Api;
import com.pr0gramm.app.api.Feed;
import com.pr0gramm.app.api.Post;

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
        long older = start.or((long) Integer.MAX_VALUE);
        return performRequest(false, query, older);
    }

    public Observable<Feed> getFeedItemsNewer(Query query, long start) {
        return performRequest(true, query, start);
    }

    private Observable<Feed> performRequest(boolean newer, Query query, long start) {

        // value for the promoted field
        int promoted = (query.getFeedType() == FeedType.PROMOTED) ? 1 : 0;

        int flags = ContentType.combine(query.getContentTypes());
        String tags = query.getTags().or("");
        String likes = query.getLikes().or("");
        String self = Strings.isNullOrEmpty(likes) ? "" : "true";

        if (newer) {
            return api.itemsGetNewer(promoted, start, flags, tags, likes, self);
        } else {
            return api.itemsGetOlder(promoted, start, flags, tags, likes, self);
        }
    }

    public Observable<Post> loadPostDetails(long id) {
        return api.info(id);
    }

    /**
     * Votes the post with the given id..
     *
     * @param id     The id of the post to vote
     * @param voting The voting to set on that post.
     */
    public Observable<Nothing> vote(long id, Vote voting) {
        return api.vote(id, voting.ordinal() - 1);
    }
}
