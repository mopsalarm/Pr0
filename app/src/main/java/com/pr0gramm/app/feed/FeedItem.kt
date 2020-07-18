package com.pr0gramm.app.feed

import android.os.Parcel
import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.parcel.*

/**
 * This is an item in pr0gramm feed item to be displayed. It is backed
 * by the data of an [Api.Feed.Item].
 */
class FeedItem : DefaultParcelable {
    val created: Instant
    val thumbnail: String
    val image: String
    val fullsize: String
    val user: String
    val userId: Long
    val id: Long
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
        userId = item.userId
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
        get() = isVideoUri(image)

    val isImage: Boolean
        get() = isImageUri(image)

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

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(id)
        dest.writeLong(promotedId)
        dest.writeLong(userId)

        dest.writeString(thumbnail)
        dest.writeString(image)
        dest.writeString(fullsize)
        dest.writeString(user)

        dest.writeInt(up)
        dest.writeInt(down)
        dest.write(created)
        dest.writeInt(width)
        dest.writeInt(height)

        dest.writeByte(mark.toByte())
        dest.writeByte(flags.toByte())
        dest.writeBooleanCompat(audio)
        dest.writeBooleanCompat(deleted)
    }

    constructor(source: Parcel) {
        id = source.readLong()
        promotedId = source.readLong()
        userId = source.readLong()

        thumbnail = source.readStringNotNull()
        image = source.readStringNotNull()
        fullsize = source.readStringNotNull()
        user = source.readStringNotNull()

        up = source.readInt()
        down = source.readInt()
        created = source.read(Instant)
        width = source.readInt()
        height = source.readInt()

        mark = source.readByte().toInt()
        flags = source.readByte().toInt()
        audio = source.readBooleanCompat()
        deleted = source.readBooleanCompat()
    }

    companion object CREATOR : ConstructorCreator<FeedItem>(::FeedItem)
}

fun isVideoUri(image: String): Boolean {
    return image.endsWith(".webm") || image.endsWith(".mp4")
}

fun isImageUri(image: String): Boolean {
    return image.endsWith(".jpg") || image.endsWith(".png")
}