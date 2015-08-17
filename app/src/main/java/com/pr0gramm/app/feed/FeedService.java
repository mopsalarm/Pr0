package com.pr0gramm.app.feed;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.inject.Singleton;
import com.pr0gramm.app.LoggerAdapter;
import com.pr0gramm.app.Track;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.api.pr0gramm.ApiGsonBuilder;
import com.pr0gramm.app.api.pr0gramm.ExtraCategoryApi;
import com.pr0gramm.app.api.pr0gramm.response.Feed;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import javax.inject.Inject;

import retrofit.RestAdapter;
import retrofit.converter.GsonConverter;
import rx.Observable;

/**
 * Performs the actual request to get the items for a feed.
 */
@Singleton
public class FeedService {
    private static final Logger logger = LoggerFactory.getLogger(FeedService.class);

    private final Api mainApi;
    private final ExtraCategoryApi extraCategoryApi;

    @Inject
    public FeedService(Api mainApi) {
        this.mainApi = mainApi;

        this.extraCategoryApi = new RestAdapter.Builder()
                .setConverter(new GsonConverter(ApiGsonBuilder.builder().create()))
                .setEndpoint("http://pr0.wibbly-wobbly.de/api/categories/v1")
                .setLog(new LoggerAdapter(logger))
                .build()
                .create(ExtraCategoryApi.class);
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

        Observable<Feed> result;
        if (query.feedFilter().getFeedType() == FeedType.RANDOM) {
            result = extraCategoryApi.random(flags);
        } else {
            result = mainApi.itemsGet(promoted, following,
                    query.older().orNull(), query.newer().orNull(), query.around().orNull(),
                    flags, tags, likes, self, user);
        }

        return result;
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
