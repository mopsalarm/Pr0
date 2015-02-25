package com.pr0gramm.app.feed;

import com.google.inject.Singleton;
import com.pr0gramm.app.ContentType;
import com.pr0gramm.app.api.Api;
import com.pr0gramm.app.api.Feed;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import rx.Observable;
import rx.functions.Func1;

/**
 */
@Singleton
public class FeedService {
    private final Api api;

    @Inject
    public FeedService(Api api) {
        this.api = api;
    }

    public Observable<List<FeedItem>> getFeed(FeedType type, Set<ContentType> flags) {
        return getFeedStartingAt(Integer.MAX_VALUE, type, flags);
    }

    public Observable<List<FeedItem>> getFeedStartingAt(long id, FeedType type,
                                                        Set<ContentType> flags) {

        int promoted = type == FeedType.PROMOTED ? 1 : 0;
        return api
                .itemsGet(promoted, id, ContentType.combine(flags))
                .map(feed -> {
                    List<FeedItem> result = new ArrayList<>();
                    for (Feed.Item item : feed.getItems())
                        result.add(new FeedItem(item, false));

                    return result;
                });
    }

}
