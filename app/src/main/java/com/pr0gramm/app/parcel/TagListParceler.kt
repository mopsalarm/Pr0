package com.pr0gramm.app.parcel

import android.os.Parcel
import android.os.Parcelable
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.util.listOfSize

/**
 */
class TagListParceler(val tags: List<Api.Tag>) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(tags.size)

        tags.forEach { tag ->
            dest.writeLong(tag.id)
            dest.writeFloat(tag.confidence)
            dest.writeString(tag.tag)
        }
    }

    companion object {
        @JvmField
        val CREATOR = creator { p ->
            val tags = listOfSize(p.readInt()) {
                val id = p.readLong()
                val confidence = p.readFloat()
                val tag = p.readString()
                Api.Tag(id, confidence, tag)
            }

            TagListParceler(tags)
        }
    }
}
