package com.pr0gramm.app.parcel

import android.os.Parcel
import android.os.Parcelable
import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.util.listOfSize

/**
 */
class CommentListParceler(val comments: List<Api.Comment>) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.apply {
            writeInt(comments.size)

            comments.forEach { comment ->
                writeLong(comment.id)
                writeFloat(comment.confidence)
                writeString(comment.name)
                writeString(comment.content)
                writeLong(comment.parent)
                writeInt(comment.up)
                writeInt(comment.down)
                writeInt(comment.mark)
                writeTyped(comment.created)
            }
        }
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<CommentListParceler> = creator { parcel ->
            val comments = listOfSize(parcel.readInt()) {
                Api.Comment(
                        id = parcel.readLong(),
                        confidence = parcel.readFloat(),
                        name = parcel.readString(),
                        content = parcel.readString(),
                        parent = parcel.readLong(),
                        up = parcel.readInt(),
                        down = parcel.readInt(),
                        mark = parcel.readInt(),
                        created = parcel.readTyped(Instant.CREATOR))
            }

            CommentListParceler(comments)
        }
    }

}