package com.pr0gramm.app.feed

import android.os.Parcel
import com.google.android.gms.common.util.Strings.emptyToNull
import com.pr0gramm.app.parcel.DefaultParcelable
import com.pr0gramm.app.parcel.SimpleCreator
import com.pr0gramm.app.services.PostCollection
import java.util.*

/**
 */
class FeedFilter : DefaultParcelable {
    var feedType: FeedType = FeedType.PROMOTED
        private set

    var tags: String? = null
        private set

    var collection: String? = null
        private set

    var collectionTitle: String? = null
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
            username = null
        }
    }

    /**
     * Tries to invert this filter. This is a best-effort and might not work.
     * If the filter can not be inverted, this method returns null
     */
    fun invert(): FeedFilter? {
        val tags = tags ?: return null
        return withTagsNoReset(Tags.invert(tags))
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

    fun withAnyCollection(owner: String): FeedFilter {
        return withCollection(owner, "**ANY", "**ANY")
    }

    fun withCollection(owner: String, collectionKey: String, collectionTitle: String): FeedFilter {
        val copy = basic()
        copy.username = normalizeString(owner)
        copy.collection = normalizeString(collectionKey)
        copy.collectionTitle = collectionTitle
        return normalize(copy)
    }

    fun withCollection(owner: String, collection: PostCollection): FeedFilter {
        val copy = basic()
        copy.username = normalizeString(collection.owner?.name ?: owner)
        copy.collection = normalizeString(collection.key)
        copy.collectionTitle = collection.titleWithOwner
        return normalize(copy)
    }

    fun withTagsNoReset(tags: String): FeedFilter {
        val copy = basic()

        if (collection != null) {
            copy.username = username
            copy.collection = collection
            copy.collectionTitle = collectionTitle
        }

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
        copy.collection = collection
        copy.collectionTitle = collectionTitle
        copy.username = username
        copy.fn()
        return normalize(copy)
    }

    override fun hashCode(): Int {
        return Objects.hash(feedType, tags, collection, username)
    }

    override fun equals(other: Any?): Boolean {
        return this === other || (other is FeedFilter
                && feedType === other.feedType
                && tags == other.tags
                && username == other.username
                && collection == other.collection)
    }

    override fun toString(): String {
        val fields = listOfNotNull(
                feedType.toString(),
                tags?.let { "tags=$tags" },
                username?.let { "username=$username" },
                collection?.let { "collection=$collection" }
        )

        return "FeedFilter(${fields.joinToString(", ")})"
    }


    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(feedType.ordinal)
        dest.writeString(tags)
        dest.writeString(username)
        dest.writeString(collection)
        dest.writeString(collectionTitle)
    }

    companion object CREATOR : SimpleCreator<FeedFilter>() {
        private val values: Array<FeedType> = FeedType.values()

        override fun createFromParcel(source: Parcel): FeedFilter {
            return FeedFilter().apply {
                this.feedType = values[source.readInt()]
                this.tags = source.readString()?.ifBlank { null }
                this.username = source.readString()?.ifBlank { null }
                this.collection = source.readString()?.ifBlank { null }
                this.collectionTitle = source.readString()?.ifBlank { null }
            }
        }

        private fun normalize(filter: FeedFilter): FeedFilter {
            // if it is a non searchable filter, we need to switch to some searchable category.
            if (!filter.feedType.searchable && !filter.isBasic) {
                return filter.withFeedType(FeedType.NEW)
            }

            if (filter.collection != null && filter.username == null) {
                return filter.copy {
                    collection = null
                    collectionTitle = null
                }
            }

            return filter
        }
    }
}

object Tags {
    fun join(lhs: String, rhs: String?): String {
        if (rhs.isNullOrBlank()) {
            return lhs
        }

        val lhsTrimmed = trimSignal(lhs)
        val rhsTrimmed = trimSignal(rhs)

        val extendedQuery = isExtendedQuery(lhs) || isExtendedQuery(rhs)
        if (extendedQuery) {
            return "! ($rhsTrimmed) ($lhsTrimmed)"
        } else {
            return "$lhsTrimmed $rhsTrimmed"
        }
    }

    fun invert(query: String): String {
        return "! -(${trimSignal(query)})"
    }

    private fun trimSignal(lhs: String): String {
        return lhs.trimStart { ch -> ch.isWhitespace() || ch == '!' || ch == '?' }
    }

    private fun isExtendedQuery(query: String): Boolean {
        val trimmed = query.trimStart()
        return trimmed.startsWith('?') || trimmed.startsWith('!')
    }
}
