package com.pr0gramm.app.feed;

import com.google.common.base.Optional;
import com.google.inject.Singleton;
import com.pr0gramm.app.ContentType;
import com.pr0gramm.app.api.Api;
import com.pr0gramm.app.api.Feed;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    public Observable<List<FeedItem>> getFeedItems(
            FeedType type, Set<ContentType> flags, Optional<FeedItem> start) {

        // value of the "older" field. depends on the feed-type.
        long older = start
                .transform(i -> (type == FeedType.PROMOTED)
                        ? i.getItem().getPromoted()
                        : i.getItem().getId())
                .or((long) Integer.MAX_VALUE);

        // value for the promoted field
        int promoted = (type == FeedType.PROMOTED) ? 1 : 0;

        return api
                .itemsGet(promoted, older, ContentType.combine(flags))
                .map(feed -> {
                    List<FeedItem> result = new ArrayList<>();
                    for (Feed.Item item : feed.getItems())
                        result.add(new FeedItem(item, false));

                    return result;
                });
    }

}
