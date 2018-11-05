package com.pr0gramm.app.feed

import com.google.android.gms.common.util.Strings.emptyToNull
import com.pr0gramm.app.parcel.Freezable
import com.pr0gramm.app.parcel.Unfreezable
import com.pr0gramm.app.parcel.parcelableCreator

/**
 */
class FeedFilter : Freezable {
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
        copy.tags = normalizeString(tags)
        return normalize(copy)
    }

    /**
     * Returns a copy of this filter that filters by the given username
     */
    fun withUser(username: String): FeedFilter {
        val copy = basic()
        copy.username = normalizeString(username)
        return normalize(copy)
    }

    /**
     * Returns a copy of this filter that filters by the likes of the given username.
     */
    fun withLikes(username: String): FeedFilter {
        val copy = basic()
        copy.likes = normalizeString(username)
        return normalize(copy)
    }

    fun withTagsNoReset(tags: String): FeedFilter {
        val copy = withLikes(likes ?: "")
        copy.tags = normalizeString(tags)
        return normalize(copy)
    }

    /**
     * Normalizes the given string by trimming it and setting empty strings to null.
     */
    private fun normalizeString(value: String): String? = emptyToNull(value.trim())

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
        return listOf(feedType, tags, likes, username).hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return this === other || (other is FeedFilter
                && feedType === other.feedType
                && tags == other.tags
                && likes == other.likes
                && username == other.username)
    }

    override fun freeze(sink: Freezable.Sink) = with(sink) {
        writeInt(feedType.ordinal)
        writeString(tags ?: "")
        writeString(likes ?: "")
        writeString(username ?: "")
    }

    companion object : Unfreezable<FeedFilter> {
        private val values: Array<FeedType> = FeedType.values()

        @JvmField
        val CREATOR = parcelableCreator()

        override fun unfreeze(source: Freezable.Source): FeedFilter {
            return FeedFilter().apply {
                this.feedType = values[source.readInt()]
                this.tags = source.readString().takeIf { it != "" }
                this.likes = source.readString().takeIf { it != "" }
                this.username = source.readString().takeIf { it != "" }
            }
        }
    }
}

object Tags {
    fun join(lhs: String, rhs: String?): String {
        if (rhs.isNullOrBlank()) {
            return lhs
        }

        val lhsTrimmed = lhs.trimStart { ch -> ch.isWhitespace() || ch == '!' || ch == '?' }
        val rhsTrimmed = rhs.trimStart { ch -> ch.isWhitespace() || ch == '!' || ch == '?' }

        val extendedQuery = isExtendedQuery(lhs) || isExtendedQuery(rhs)
        if (extendedQuery) {
            return "! ($lhsTrimmed) ($rhsTrimmed)"
        } else {
            return "$lhsTrimmed $rhsTrimmed"
        }
    }

    private fun isExtendedQuery(query: String): Boolean {
        val trimmed = query.trimStart()
        return trimmed.startsWith('?') || trimmed.startsWith('!')
    }
}
