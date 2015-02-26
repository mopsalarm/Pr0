package com.pr0gramm.app.feed;

import android.support.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.pr0gramm.app.ContentType;

import java.util.EnumSet;
import java.util.Set;

/**
 *
 */
public final class Query {
    private FeedType feedType;
    private Set<ContentType> contentTypes;
    private Optional<String> tags;

    public Query() {
        feedType = FeedType.PROMOTED;
        contentTypes = EnumSet.of(ContentType.SFW);
        tags = Optional.absent();
    }

    Query(Query other) {
        feedType = other.feedType;
        contentTypes = EnumSet.copyOf(other.contentTypes);
        tags = other.tags;
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
}
