package com.pr0gramm.app.orm;

import com.google.common.base.Optional;
import com.orm.SugarRecord;
import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.feed.FeedType;

import static com.google.common.base.Objects.equal;

/**
 */
public final class Bookmark extends SugarRecord<Bookmark> {
    private String title;
    private String filterTags;
    private String filterUsername;
    private String filterFeedType;

    // for sugar orm
    public Bookmark() {
    }

    public String getTitle() {
        return title;
    }

    public FeedFilter asFeedFilter() {
        FeedFilter filter = new FeedFilter()
                .withFeedType(FeedType.valueOf(filterFeedType));

        if (filterTags != null)
            filter = filter.withTags(filterTags);

        if (filterUsername != null)
            filter = filter.withUser(filterUsername);

        return filter;
    }

    public static Bookmark of(FeedFilter filter, String title) {
        Bookmark entry = new Bookmark();
        entry.title = title;
        entry.filterTags = filter.getTags().orNull();
        entry.filterUsername = filter.getUsername().orNull();
        entry.filterFeedType = filter.getFeedType().toString();
        return entry;
    }

    public static Optional<Bookmark> byFilter(FeedFilter filter) {
        for (Bookmark bookmark : Bookmark.find(Bookmark.class, null)) {
            if (equal(filter, bookmark.asFeedFilter()))
                return Optional.of(bookmark);
        }

        return Optional.absent();
    }
}
