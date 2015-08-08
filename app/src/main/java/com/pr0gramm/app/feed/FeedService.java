package com.pr0gramm.app.feed;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.inject.Singleton;
import com.pr0gramm.app.Track;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.api.pr0gramm.response.Feed;
import com.pr0gramm.app.api.pr0gramm.response.Post;

import org.immutables.value.Value;

import java.util.Set;

import javax.inject.Inject;

import rx.Observable;

/**
 * Performs the actual request to get the items for a feed.
 */
@Singleton
public class FeedService {
    private final Api api;

    @Inject
    public FeedService(Api api) {
        this.api = api;
    }

    public Observable<Feed> getFeedItems(FeedQuery query) {
        FeedFilter feedFilter = query.feedFilter();
        Track.requestFeed(feedFilter.getFeedType());

        // filter by feed-type
        Integer promoted = (feedFilter.getFeedType() == FeedType.PROMOTED) ? 1 : null;
        Integer following = (feedFilter.getFeedType() == FeedType.PREMIUM) ? 1 : null;

        int flags = ContentType.combine(query.contentTypes());
        String tags = feedFilter.getTags().orNull();
        String user = feedFilter.getUsername().orNull();

        // FIXME this is quite hacky right now.
        String likes = feedFilter.getLikes().orNull();
        Boolean self = Strings.isNullOrEmpty(likes) ? null : true;

        Observable<Feed> result = api.itemsGet(promoted, following,
                query.older().orNull(), query.newer().orNull(), query.around().orNull(),
                flags, tags, likes, self, user);

        return result.map(this::filterPreloadedOnly);
    }

    private Feed filterPreloadedOnly(Feed feed) {
        return feed;
    }

    public Observable<Post> loadPostDetails(long id) {
        return api.info(id);
    }

    @Value.Immutable
    public interface FeedQuery {
        FeedFilter feedFilter();

        Set<ContentType> contentTypes();

        Optional<Long> newer();

        Optional<Long> older();

        Optional<Long> around();
    }
}
