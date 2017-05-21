package com.pr0gramm.app.feed

import android.os.Parcel
import android.os.Parcelable
import com.google.common.base.Objects
import com.google.common.base.Optional
import com.google.common.base.Strings.emptyToNull
import com.pr0gramm.app.parcel.core.creator

/**
 */
class FeedFilter() : Parcelable {
    var feedType: FeedType = FeedType.PROMOTED
        private set

    var tags: String? = null
        private set

    var likes: String? = null
        private set

    var username: String? = null
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
            tags = null
            likes = null
            username = null
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
        val copy = withLikes(likes ?: "")
        copy.tags = fromString(tags)
        return normalize(copy)
    }

    /**
     * Creates an []Optional] from a string - trims the input and creates an empty
     * [Optional] from empty strings.
     */
    private fun fromString(value: String): String? = emptyToNull(value.trim())

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
        dest.writeString(tags)
        dest.writeString(likes)
        dest.writeString(username)
    }

    internal constructor(p: Parcel) : this() {
        val feedType = p.readInt()
        this.feedType = FeedType.values()[feedType]
        this.tags = p.readString()
        this.likes = p.readString()
        this.username = p.readString()
    }

    companion object {
        @JvmField
        val CREATOR = creator(::FeedFilter)
    }
}
