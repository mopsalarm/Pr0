package com.pr0gramm.app.feed;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.base.Objects;
import com.google.common.base.Optional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import static com.google.common.base.Objects.equal;
import static com.google.common.base.Optional.absent;
import static com.google.common.base.Optional.fromNullable;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.collect.Iterables.toArray;
import static java.util.Arrays.asList;

/**
 */
public final class FeedFilter implements Parcelable {
    private FeedType feedType;
    private Set<ContentType> contentTypes;
    private Optional<String> tags = absent();
    private Optional<String> likes = absent();
    private Optional<String> username = absent();

    public FeedFilter() {
        feedType = FeedType.PROMOTED;
        contentTypes = EnumSet.of(ContentType.SFW);
    }

    FeedFilter(FeedFilter other) {
        feedType = other.feedType;
        contentTypes = EnumSet.copyOf(other.contentTypes);
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
     * Checks if this filter is a basic filter.
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
        return result;
    }

    public FeedType getFeedType() {
        return feedType;
    }

    /**
     * Returns a copy of this filter that filters by content type.
     */
    public FeedFilter withContentType(Set<ContentType> types) {
        FeedFilter feedFilter = new FeedFilter(this);
        feedFilter.contentTypes = EnumSet.copyOf(types);
        return feedFilter;
    }

    public Set<ContentType> getContentTypes() {
        return contentTypes;
    }

    /**
     * Returns a copy of this filter that will filter by the given tag
     */
    public FeedFilter withTags(@Nonnull String tags) {
        FeedFilter copy = basic();
        copy.tags = fromNullable(emptyToNull(tags.trim()));
        return copy;
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
        return copy;
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
        return copy;
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
                //&& equal(contentTypes, other.contentTypes)
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
        dest.writeParcelableArray(toArray(contentTypes, ContentType.class), flags);
        dest.writeString(tags.orNull());
        dest.writeString(likes.orNull());
        dest.writeString(username.orNull());
    }

    @SuppressWarnings("unchecked")
    private FeedFilter(Parcel in) {
        int tmpFeedType = in.readInt();
        this.feedType = tmpFeedType == -1 ? null : FeedType.values()[tmpFeedType];
        this.contentTypes = EnumSet.copyOf((List) asList(
                in.readParcelableArray(ContentType.class.getClassLoader())));

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
