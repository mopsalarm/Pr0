package com.pr0gramm.app.feed;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.pr0gramm.app.ContentType;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.google.common.collect.Iterables.toArray;
import static java.util.Arrays.asList;

/**
 *
 */
public final class Query implements Parcelable {
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


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.feedType == null ? -1 : this.feedType.ordinal());
        dest.writeParcelableArray(toArray(contentTypes, ContentType.class), flags);
        dest.writeString(tags.orNull());
    }

    @SuppressWarnings("unchecked")
    private Query(Parcel in) {
        int tmpFeedType = in.readInt();
        this.feedType = tmpFeedType == -1 ? null : FeedType.values()[tmpFeedType];
        this.contentTypes = EnumSet.copyOf((List) asList(
                in.readParcelableArray(ContentType.class.getClassLoader())));

        this.tags = Optional.fromNullable(in.readString());
    }

    public static final Parcelable.Creator<Query> CREATOR = new Parcelable.Creator<Query>() {
        public Query createFromParcel(Parcel source) {
            return new Query(source);
        }

        public Query[] newArray(int size) {
            return new Query[size];
        }
    };
}
