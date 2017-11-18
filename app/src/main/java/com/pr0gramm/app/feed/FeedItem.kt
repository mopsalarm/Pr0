package com.pr0gramm.app.feed

import android.os.Parcel
import android.os.Parcelable
import com.pr0gramm.app.HasThumbnail
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.parcel.core.creator
import org.joda.time.Instant

/**
 * This is an item in pr0gramm feed item to be displayed. It is backed
 * by the data of an [Api.Feed.Item].
 */
class FeedItem : Parcelable, HasThumbnail {
    val created: Instant
    val thumbnail: String
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

    val isGIF: Boolean
        get() = image.endsWith(".gif")

    override fun id(): Long {
        return _id.toLong()
    }

    override fun thumbnail(): String {
        return thumbnail
    }

    val id: Long
        get() = this._id.toLong()

    val promotedId: Long
        get() = _promotedId.toLong()

    val mark: Int
        get() = _mark.toInt()

    val flags: Int
        get() = _flags.toInt()

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

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, f: Int) {
        // combine up/down as rating.
        dest.writeInt(this._id)
        dest.writeInt(this._promotedId)

        dest.writeString(this.thumbnail)
        dest.writeString(this.image)
        dest.writeString(this.fullsize)
        dest.writeString(this.user)

        dest.writeInt(_rating)
        dest.writeInt((created.millis / 1000).toInt())
        dest.writeInt(width)
        dest.writeInt(height)

        val mfa = (_mark.toInt() shl 16) or (_flags.toInt() shl 8) or (if (audio) 1 else 0)
        dest.writeInt(mfa)
    }

    internal constructor(parcel: Parcel) {
        this._id = parcel.readInt()
        this._promotedId = parcel.readInt()

        this.thumbnail = parcel.readString()
        this.image = parcel.readString()
        this.fullsize = parcel.readString()
        this.user = parcel.readString()

        this._rating = parcel.readInt()
        this.created = Instant(1000L * parcel.readInt())
        this.width = parcel.readInt()
        this.height = parcel.readInt()

        val mfa = parcel.readInt()
        this._mark = ((mfa shr 16) and 0xff).toByte()
        this._flags = ((mfa shr 8) and 0xff).toByte()
        this.audio = (mfa and 0xff) != 0
    }

    companion object {
        @JvmField
        val CREATOR = creator(::FeedItem)
    }
}
