package com.pr0gramm.app.feed

import android.os.Parcel
import android.os.Parcelable
import com.google.common.base.Objects
import com.google.common.base.Optional
import com.google.common.base.Optional.absent
import com.google.common.base.Optional.fromNullable
import com.google.common.base.Strings.emptyToNull

/**
 */
class FeedFilter() : Parcelable {
    var feedType: FeedType = FeedType.PROMOTED
        private set

    var tags: Optional<String> = absent<String>()
        private set

    var likes: Optional<String> = absent<String>()
        private set

    var username: Optional<String> = absent<String>()
        private set

    /**
     * Checks if this filter is a basic filter. A filter is basic, if
     * it has no tag/likes or username-filter.
     */
    val isBasic: Boolean
        get() = equals(basic())

    /**
     * Returns a copy of this filter with all optional constraints removed.
     * This removes tags, username-filter and the likes.
     */
    fun basic(): FeedFilter {
        return copy {
            tags = Optional.absent<String>()
            likes = Optional.absent<String>()
            username = Optional.absent<String>()
        }
    }

    /**
     * Returns a copy of this filter that filters by the given feed type.
     */
    fun withFeedType(type: FeedType): FeedFilter {
        return copy {
            feedType = type
        }
    }

    /**
     * Returns a copy of this filter that will filter by the given tag
     */
    fun withTags(tags: String): FeedFilter {
        val copy = basic()
        copy.tags = fromString(tags)
        return normalize(copy)
    }

    /**
     * Returns a copy of this filter that filters by the given username
     */
    fun withUser(username: String): FeedFilter {
        val copy = basic()
        copy.username = fromString(username)
        return normalize(copy)
    }

    /**
     * Returns a copy of this filter that filters by the likes of the given username.
     */
    fun withLikes(username: String): FeedFilter {
        val copy = basic()
        copy.likes = fromString(username)
        return normalize(copy)
    }

    fun withTagsNoReset(tags: String): FeedFilter {
        val copy = withLikes(likes.or(""))
        copy.tags = fromString(tags)
        return normalize(copy)
    }

    /**
     * Creates an []Optional] from a string - trims the input and creates an empty
     * [Optional] from empty strings.
     */
    private fun fromString(value: String) = fromNullable(emptyToNull(value.trim()))

    private fun copy(fn: FeedFilter.() -> Unit): FeedFilter {
        val copy = FeedFilter()
        copy.feedType = feedType
        copy.tags = tags
        copy.likes = likes
        copy.username = username
        copy.fn()
        return normalize(copy)
    }

    private fun normalize(filter: FeedFilter): FeedFilter {
        // if it is a non searchable filter, we need to switch to some searchable category.
        if (!filter.feedType.searchable && !filter.isBasic) {
            return filter.withFeedType(FeedType.NEW)
        }

        return filter
    }

    override fun hashCode(): Int {
        return Objects.hashCode(feedType, tags, likes, username)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is FeedFilter)
            return false

        return this === other || feedType === other.feedType && tags == other.tags
                && likes == other.likes && username == other.username
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, f: Int) {
        dest.writeInt(feedType.ordinal)
        dest.writeString(tags.orNull())
        dest.writeString(likes.orNull())
        dest.writeString(username.orNull())
    }

    internal constructor(p: Parcel) : this() {
        val feedType = p.readInt()
        this.feedType = FeedType.values()[feedType]
        this.tags = fromNullable(p.readString())
        this.likes = fromNullable(p.readString())
        this.username = fromNullable(p.readString())
    }

    companion object {
        @JvmStatic
        val CREATOR: Parcelable.Creator<FeedFilter> = object : Parcelable.Creator<FeedFilter> {
            override fun createFromParcel(source: Parcel): FeedFilter {
                return FeedFilter(source)
            }

            override fun newArray(size: Int): Array<FeedFilter?> {
                return arrayOfNulls(size)
            }
        }
    }
}
