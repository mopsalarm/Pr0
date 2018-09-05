package com.pr0gramm.app.feed

import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.HasThumbnail
import com.pr0gramm.app.parcel.Freezable
import com.pr0gramm.app.parcel.Unfreezable
import com.pr0gramm.app.parcel.parcelableCreator
import com.pr0gramm.app.util.toInt

/**
 * This is an item in pr0gramm feed item to be displayed. It is backed
 * by the data of an [Api.Feed.Item].
 */
class FeedItem : Freezable, HasThumbnail {
    val created: Instant
    override val thumbnail: String
    val image: String
    val fullsize: String
    val user: String
    override val id: Long
    val promotedId: Long
    val width: Int
    val height: Int
    val up: Int
    val down: Int
    val mark: Int
    val flags: Int
    val audio: Boolean
    val deleted: Boolean

    constructor(item: Api.Feed.Item) {
        id = item.id
        promotedId = item.promoted
        thumbnail = item.thumb
        image = item.image
        fullsize = item.fullsize
        user = item.user
        up = item.up
        down = item.down
        mark = item.mark
        created = item.created
        flags = item.flags
        width = item.width
        height = item.height
        audio = item.audio
        deleted = item.deleted
    }

    /**
     * Returns the content type of this Item, falling back to [ContentType.SFW]
     * if no type is available.
     */
    val contentType: ContentType
        get() = ContentType.valueOf(flags) ?: ContentType.SFW

    val isVideo: Boolean
        get() = image.endsWith(".webm") || image.endsWith(".mp4")

    val isPinned: Boolean
        get() = promotedId > 1_000_000_000

    /**
     * Gets the id of this feed item depending on the type of the feed..

     * @param type The type of feed.
     */
    fun id(type: FeedType): Long {
        return (if (type === FeedType.PROMOTED) promotedId else id)
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is FeedItem && other.id == id
    }

    override fun toString(): String = "FeedItem(id=$id)"

    override fun freeze(sink: Freezable.Sink): Unit = with(sink) {
        sink.writeLong(id)
        sink.writeLong(promotedId)

        sink.writeString(thumbnail)
        sink.writeString(image)
        sink.writeString(fullsize)
        sink.writeString(user)

        sink.writeShort(up)
        sink.writeShort(down)
        sink.writeInt((created.millis / 1000).toInt())
        sink.writeInt(width)
        sink.writeInt(height)

        sink.writeByte(mark)
        sink.writeByte(flags)
        sink.writeByte(audio.toInt())
        sink.writeByte(deleted.toInt())
    }

    constructor(source: Freezable.Source) {
        id = source.readLong()
        promotedId = source.readLong()

        thumbnail = source.readString()
        image = source.readString()
        fullsize = source.readString()
        user = source.readString()

        up = source.readShort().toInt()
        down = source.readShort().toInt()
        created = Instant(1000L * source.readInt())
        width = source.readInt()
        height = source.readInt()

        mark = source.readByte().toInt()
        flags = source.readByte().toInt()
        audio = source.readByte() != 0.toByte()
        deleted = source.readByte() != 0.toByte()
    }

    companion object : Unfreezable<FeedItem> {
        @JvmField
        val CREATOR = parcelableCreator()

        override fun unfreeze(source: Freezable.Source): FeedItem = FeedItem(source)
    }
}
