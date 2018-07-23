package com.pr0gramm.app.feed

import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.HasThumbnail
import com.pr0gramm.app.parcel.Freezable
import com.pr0gramm.app.parcel.Unfreezable
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
    val width: Int
    val height: Int

    private val _id: Int
    private val _promotedId: Int
    private val _rating: Int
    private val _mark: Byte
    private val _flags: Byte

    val audio: Boolean

    constructor(item: Api.Feed.Item) {
        _id = item.id.toInt()
        _promotedId = item.promoted.toInt()
        thumbnail = item.thumb
        image = item.image
        fullsize = item.fullsize
        user = item.user
        _rating = ((item.up shl 16) and 0xffff0000.toInt()) or (item.down and 0xffff)
        _mark = item.mark.toByte()
        created = item.created
        _flags = item.flags.toByte()
        width = item.width ?: 0
        height = item.height ?: 0
        audio = item.audio ?: false
    }

    val up: Int
        get() = (_rating shr 16) and 0xffff

    val down: Int
        get() = _rating and 0xffff

    /**
     * Returns the content type of this Item, falling back to [ContentType.SFW]
     * if no type is available.
     */
    val contentType: ContentType
        get() = ContentType.valueOf(_flags.toInt()) ?: ContentType.SFW

    val isVideo: Boolean
        get() = image.endsWith(".webm") || image.endsWith(".mp4")

    override val id: Long
        get() = this._id.toLong()

    val promotedId: Long
        get() = _promotedId.toLong()

    val mark: Int
        get() = _mark.toInt()

    val flags: Int
        get() = _flags.toInt()

    val isPinned: Boolean
        get() = _promotedId > 1_000_000_000

    /**
     * Gets the id of this feed item depending on the type of the feed..

     * @param type The type of feed.
     */
    fun id(type: FeedType): Long {
        return (if (type === FeedType.PROMOTED) promotedId else id)
    }

    override fun hashCode(): Int {
        return _id
    }

    override fun equals(other: Any?): Boolean {
        return other is FeedItem && other._id == _id
    }

    override fun freeze(sink: Freezable.Sink): Unit = with(sink) {
        sink.writeInt(_id)
        sink.writeInt(_promotedId)

        sink.writeString(thumbnail)
        sink.writeString(image)
        sink.writeString(fullsize)
        sink.writeString(user)

        sink.writeInt(_rating)
        sink.writeInt((created.millis / 1000).toInt())
        sink.writeInt(width)
        sink.writeInt(height)

        sink.writeByte(_mark.toInt())
        sink.writeByte(_flags.toInt())
        sink.writeByte(audio.toInt())
    }

    constructor(source: Freezable.Source) {
        _id = source.readInt()
        _promotedId = source.readInt()

        thumbnail = source.readString()
        image = source.readString()
        fullsize = source.readString()
        user = source.readString()

        _rating = source.readInt()
        created = Instant(1000L * source.readInt())
        width = source.readInt()
        height = source.readInt()

        _mark = source.readByte()
        _flags = source.readByte()
        audio = source.readByte() != 0.toByte()
    }

    companion object : Unfreezable<FeedItem> {
        override fun unfreeze(source: Freezable.Source): FeedItem = FeedItem(source)
    }
}
