package com.pr0gramm.app.feed;

import android.support.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.inject.Singleton;
import com.pr0gramm.app.ContentType;
import com.pr0gramm.app.api.Api;
import com.pr0gramm.app.api.Feed;

import java.util.ArrayList;
import java.util.EnumSet;
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

    public Observable<List<FeedItem>> getFeedItems(Query query) {

        // value of the "older" field. depends on the feed-type.
        long older = query.getStart()
                .transform(i -> (query.getFeedType() == FeedType.PROMOTED)
                        ? i.getItem().getPromoted()
                        : i.getItem().getId())
                .or((long) Integer.MAX_VALUE);

        // value for the promoted field
        int promoted = (query.getFeedType() == FeedType.PROMOTED) ? 1 : 0;

        int flags = ContentType.combine(query.getContentTypes());
        String tags = query.getTags().or("");

        return api
                .itemsGet(promoted, older, flags, tags)
                .map(feed -> {
                    List<FeedItem> result = new ArrayList<>();
                    for (Feed.Item item : feed.getItems())
                        result.add(new FeedItem(item, false));

                    return result;
                });
    }

    public static final class Query {
        private FeedType feedType;
        private Set<ContentType> contentTypes;
        private Optional<String> tags;
        private Optional<FeedItem> start;

        public Query() {
            feedType = FeedType.PROMOTED;
            contentTypes = EnumSet.of(ContentType.SFW);
            tags = Optional.absent();
            start = Optional.absent();
        }

        private Query(Query other) {
            feedType = other.feedType;
            contentTypes = EnumSet.copyOf(other.contentTypes);
            tags = other.tags;
            start = other.start;
        }

        public FeedType getFeedType() {
            return feedType;
        }

        public Set<ContentType> getContentTypes() {
            return contentTypes;
        }

        public Optional<String> getTags() {
            return tags;
        }

        public Optional<FeedItem> getStart() {
            return start;
        }

        public Query withFeedType(FeedType type) {
            Query result = new Query(this);
            result.feedType = type;
            return result;
        }

        public Query withContentType(Set<ContentType> types) {
            Query query = new Query(this);
            query.contentTypes = EnumSet.copyOf(types);
            return query;
        }

        public Query withNoTags() {
            return withTags(null);
        }

        public Query withTags(@Nullable String tags) {
            Query query = new Query(this);

            if (tags != null)
                tags = tags.trim();

            query.tags = Optional.fromNullable(Strings.emptyToNull(tags));
            return query;
        }

        public Query withStart(FeedItem start) {
            Query query = new Query(this);
            query.start = Optional.fromNullable(start);
            return query;
        }

        public Query withStart(Optional<FeedItem> start) {
            Query query = new Query(this);
            query.start = start;
            return query;
        }
    }
}
