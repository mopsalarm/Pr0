package com.pr0gramm.app.feed;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.inject.Singleton;
import com.pr0gramm.app.ContentType;
import com.pr0gramm.app.VoteService;
import com.pr0gramm.app.api.Api;
import com.pr0gramm.app.api.Feed;
import com.pr0gramm.app.api.Post;

import javax.inject.Inject;

import rx.Observable;

import static com.orm.SugarTransactionHelper.doInTansaction;

/**
 */
@Singleton
public class FeedService {
    private final Api api;
    private final VoteService voteService;

    @Inject
    public FeedService(Api api, VoteService voteService) {
        this.api = api;
        this.voteService = voteService;
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

        // FIXME this is quite hacky right now.
        String likes = query.getLikes().orNull();
        String self = Strings.isNullOrEmpty(likes) ? null : "true";

        Observable<Feed> response = api.itemsGet(promoted, older.orNull(), newer.orNull(), flags, tags, likes, self);
        return response.map(feed -> {
            // intercept our own favorites and store them in the database.
            if ("true".equals(self)) {
                doInTansaction(() -> {
                    for (Feed.Item item : feed.getItems()) {
                        voteService.storeVoteValue(item.getId(), Vote.FAVORITE);
                    }
                });
            }

            return feed;
        });
    }

    public Observable<Post> loadPostDetails(long id) {
        return api.info(id);
    }
}
