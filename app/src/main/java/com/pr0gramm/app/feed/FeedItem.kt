package com.pr0gramm.app.feed

import android.os.Parcel
import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.parcel.*

/**
 * This is an item in pr0gramm feed item to be displayed. It is backed
 * by the data of an [Api.Feed.Item].
 */
data class FeedItem(
        val created: Instant,
        val thumbnail: String,
        val image: String,
        val fullsize: String,
        val user: String,
        val userId: Long,
        val id: Long,
        val promotedId: Long,
        val width: Int,
        val height: Int,
        val up: Int,
        val down: Int,
        val mark: Int,
        val flags: Int,
        val audio: Boolean,
        val deleted: Boolean,
        val placeholder: Boolean,
) : DefaultParcelable {
    constructor(item: Api.Feed.Item) : this(
            id = item.id,
            promotedId = item.promoted,
            userId = item.userId,
            thumbnail = item.thumb,
            image = item.image,
            fullsize = item.fullsize,
            user = item.user,
            up = item.up,
            down = item.down,
            mark = item.mark,
            created = item.created,
            flags = item.flags,
            width = item.width,
            height = item.height,
            audio = item.audio,
            deleted = item.deleted,
            placeholder = false,
    )

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

    override fun toString(): String = "FeedItem(id=$id)"

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(id.toInt())
        dest.writeInt(promotedId.toInt())
        dest.writeInt(userId.toInt())

        // deduplicate values for thumb & fullsize if equal to 'image'
        dest.writeString(image)
        dest.writeString(thumbnail.takeIf { it != image })
        dest.writeString(fullsize.takeIf { it != image })

        dest.writeString(user)

        dest.writeInt(up)
        dest.writeInt(down)
        dest.write(created)
        dest.writeInt(width)
        dest.writeInt(height)

        var bits = 0

        bits = bits.or(if (audio) 0b0001 else 0)
        bits = bits.or(if (deleted) 0b0010 else 0)
        bits = bits.or(if (placeholder) 0b0100 else 0)

        bits = bits.or((mark and 0xff) shl 8)
        bits = bits.or((flags and 0xff) shl 16)

        dest.writeInt(bits)
    }

    companion object CREATOR : ConstructorCreator<FeedItem>({ source ->
        val id = source.readInt().toLong()
        val promotedId = source.readInt().toLong()
        val userId = source.readInt().toLong()

        // deduplicate values for thumb & fullsize if equal to image
        val image = source.readStringNotNull()
        val thumbnail = source.readString() ?: image
        val fullsize = source.readString() ?: image

        val user = source.readStringNotNull()

        val up = source.readInt()
        val down = source.readInt()
        val created = source.read(Instant)
        val width = source.readInt()
        val height = source.readInt()

        val bits = source.readInt()

        val audio = (bits and 0b0001) != 0
        val deleted = (bits and 0b0010) != 0
        val placeholder = (bits and 0b0100) != 0

        val mark = (bits ushr 8) and 0xff
        val flags = (bits ushr 16) and 0xff

        FeedItem(
                id = id,
                promotedId = promotedId,
                userId = userId,
                image = image,
                thumbnail = thumbnail,
                fullsize = fullsize,
                user = user,
                up = up,
                down = down,
                created = created,
                width = width,
                height = height,
                mark = mark,
                flags = flags,
                audio = audio,
                deleted = deleted,
                placeholder = placeholder,
        )
    })
}

fun isVideoUri(image: String): Boolean {
    return image.endsWith(".webm") || image.endsWith(".mp4")
}

fun isImageUri(image: String): Boolean {
    return image.endsWith(".jpg") || image.endsWith(".png")
}
