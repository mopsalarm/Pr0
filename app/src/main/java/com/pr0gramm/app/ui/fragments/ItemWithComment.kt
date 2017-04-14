package com.pr0gramm.app.ui.fragments

import android.os.Parcel
import android.os.Parcelable
import com.pr0gramm.app.parcel.core.creator

/**
 */
class ItemWithComment(val itemId: Long, val commentId: Long?) : Parcelable {
    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, f: Int) {
        dest.writeLong(this.itemId)
        dest.writeValue(this.commentId)
    }

    internal constructor(parcel: Parcel) : this(
            itemId = parcel.readLong(),
            commentId = parcel.readValue(Long::class.java.classLoader) as Long) {
    }

    companion object {
        val CREATOR = creator(::ItemWithComment)
    }
}
