package com.pr0gramm.app.parcel

import android.os.Parcel
import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Api

/**
 */
class CommentListParceler(val comments: List<Api.Comment>) : DefaultParcelable {

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeValues(comments) { comment ->
            writeLong(comment.id)
            writeLong(comment.parentId)
            writeFloat(comment.confidence)
            writeInt(comment.up)
            writeInt(comment.down)
            writeByte(comment.mark.toByte())
            write(comment.created)
            writeString(comment.name)
            writeString(comment.content)
        }
    }

    companion object CREATOR : SimpleCreator<CommentListParceler>() {
        override fun createFromParcel(source: Parcel): CommentListParceler {
            val comments = source.readValues {
                Api.Comment(
                        id = readLong(),
                        parentId = readLong(),
                        confidence = readFloat(),
                        up = readInt(),
                        down = readInt(),
                        mark = readByte().toInt(),
                        created = read(Instant),
                        name = readStringNotNull(),
                        content = readStringNotNull())
            }

            return CommentListParceler(comments)
        }
    }
}