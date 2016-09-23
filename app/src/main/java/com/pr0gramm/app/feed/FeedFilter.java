package com.pr0gramm.app.feed;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.base.Objects;
import com.google.common.base.Optional;

import javax.annotation.Nonnull;

import static com.google.common.base.Objects.equal;
import static com.google.common.base.Optional.absent;
import static com.google.common.base.Optional.fromNullable;
import static com.google.common.base.Strings.emptyToNull;

/**
 */
public final class FeedFilter implements Parcelable {
    private FeedType feedType;
    private Optional<String> tags = absent();
    private Optional<String> likes = absent();
    private Optional<String> username = absent();

    public FeedFilter() {
        feedType = FeedType.PROMOTED;
    }

    FeedFilter(FeedFilter other) {
        feedType = other.feedType;
        tags = other.tags;
        likes = other.likes;
        username = other.username;
    }

    /**
     * Returns a copy of this filter with all optional constraints removed.
     * This removes tags, username-filter and the likes.
     */
    public FeedFilter basic() {
        FeedFilter copy = new FeedFilter(this);
        copy.tags = Optional.absent();
        copy.likes = Optional.absent();
        copy.username = Optional.absent();
        return copy;
    }

    /**
     * Checks if this filter is a basic filter. A filter is basic, if
     * it has no tag/likes or username-filter.
     */
    public boolean isBasic() {
        return equals(basic());
    }

    /**
     * Returns a copy of this filter that filters by the given feed type.
     */
    public FeedFilter withFeedType(FeedType type) {
        FeedFilter result = new FeedFilter(this);
        result.feedType = type;
        return fix(result);
    }

    private static FeedFilter fix(FeedFilter filter) {
        // if it is a non searchable filter, we need to switch to some searchable category.
        if (!filter.getFeedType().searchable() && !filter.isBasic())
            filter = filter.withFeedType(FeedType.NEW);

        return filter;
    }

    public FeedType getFeedType() {
        return feedType;
    }

    /**
     * Returns a copy of this filter that will filter by the given tag
     */
    public FeedFilter withTags(@Nonnull String tags) {
        FeedFilter copy = basic();
        copy.tags = fromNullable(emptyToNull(tags.trim()));
        return fix(copy);
    }

    /**
     * Returns the tags for this filter.
     */
    public Optional<String> getTags() {
        return tags;
    }

    /**
     * Returns a copy of this filter that filters by the given username
     */
    public FeedFilter withUser(@Nonnull String username) {
        FeedFilter copy = basic();
        copy.username = fromNullable(emptyToNull(username.trim()));
        return fix(copy);
    }

    public Optional<String> getUsername() {
        return username;
    }

    /**
     * Returns a copy of this filter that filters by the likes of the given username.
     */
    public FeedFilter withLikes(@Nonnull String username) {
        FeedFilter copy = basic();
        copy.likes = fromNullable(emptyToNull(username.trim()));
        return fix(copy);
    }

    public FeedFilter withTagsNoReset(String tags) {
        FeedFilter copy = withLikes(likes.or(""));
        copy.tags = fromNullable(emptyToNull(tags.trim()));
        return fix(copy);
    }

    public Optional<String> getLikes() {
        return likes;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(feedType, /*contentTypes,*/ tags, likes, username);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != FeedFilter.class)
            return false;

        FeedFilter other = (FeedFilter) obj;
        return this == other || feedType == other.feedType
                && equal(tags, other.tags)
                && equal(likes, other.likes)
                && equal(username, other.username);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.feedType == null ? -1 : this.feedType.ordinal());
        dest.writeString(tags.orNull());
        dest.writeString(likes.orNull());
        dest.writeString(username.orNull());
    }

    @SuppressWarnings("unchecked")
    FeedFilter(Parcel in) {
        int tmpFeedType = in.readInt();
        this.feedType = tmpFeedType == -1 ? null : FeedType.values()[tmpFeedType];
        this.tags = fromNullable(in.readString());
        this.likes = fromNullable(in.readString());
        this.username = fromNullable(in.readString());
    }

    public static final Parcelable.Creator<FeedFilter> CREATOR = new Parcelable.Creator<FeedFilter>() {
        public FeedFilter createFromParcel(Parcel source) {
            return new FeedFilter(source);
        }

        public FeedFilter[] newArray(int size) {
            return new FeedFilter[size];
        }
    };
}
