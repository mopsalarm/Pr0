package com.pr0gramm.app.ui.fragments

import android.os.Parcel
import android.os.Parcelable
import com.pr0gramm.app.Instant
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.parcel.creator

/**
 */
data class CommentRef(val itemId: Long, val commentId: Long? = null, val notificationTime: Instant? = null) : Parcelable {
    constructor(item: FeedItem) : this(item.id)

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, f: Int) {
        dest.writeLong(itemId)
        dest.writeLong(commentId ?: -1L)
        dest.writeLong(notificationTime?.millis ?: 0)
    }

    companion object {
        @JvmField
        val CREATOR = creator { parcel ->
            val itemId = parcel.readLong()
            val commentId = parcel.readLong().takeIf { it != -1L }
            val notificationTime = parcel.readLong().takeIf { it > 0 }?.let { Instant(it) }
            CommentRef(itemId, commentId, notificationTime)
        }
    }
}
