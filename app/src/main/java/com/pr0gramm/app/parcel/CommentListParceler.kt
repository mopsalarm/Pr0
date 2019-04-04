package com.pr0gramm.app.parcel

import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Api

/**
 */
class CommentListParceler(val comments: List<Api.Comment>) : Freezable {

    override fun freeze(sink: Freezable.Sink) = with(sink) {
        writeValues(comments.size) { idx ->
            val comment = comments[idx]
            writeLong(comment.id)
            writeFloat(comment.confidence)
            writeString(comment.name)
            writeString(comment.content)
            writeLong(comment.parent)
            writeShort(comment.up)
            writeShort(comment.down)
            writeByte(comment.mark)
            write(comment.created)
        }
    }

    companion object : Unfreezable<CommentListParceler> {
        @JvmField
        val CREATOR = parcelableCreator()

        override fun unfreeze(source: Freezable.Source): CommentListParceler {
            val comments = source.readValues {
                Api.Comment(
                        id = source.readLong(),
                        confidence = source.readFloat(),
                        name = source.readString(),
                        content = source.readString(),
                        parent = source.readLong(),
                        up = source.readShort().toInt(),
                        down = source.readShort().toInt(),
                        mark = source.readByte().toInt(),
                        created = source.read(Instant))
            }

            return CommentListParceler(comments)
        }
    }

}