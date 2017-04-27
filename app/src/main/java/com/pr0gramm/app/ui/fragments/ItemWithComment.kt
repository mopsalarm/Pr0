package com.pr0gramm.app.ui.fragments

import android.os.Parcel
import android.os.Parcelable
import com.pr0gramm.app.parcel.core.creator

/**
 */
class ItemWithComment(val itemId: Long, val commentId: Long? = null) : Parcelable {
    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, f: Int) {
        dest.writeLong(this.itemId)
        dest.writeLong(this.commentId ?: -1L)
    }

    internal constructor(parcel: Parcel) : this(
            itemId = parcel.readLong(),
            commentId = parcel.readLong().let { if (it == -1L) null else it }) {
    }

    companion object {
        @JvmField
        val CREATOR = creator(::ItemWithComment)
    }
}
