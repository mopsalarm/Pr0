package com.pr0gramm.app.parcel

import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Api

/**
 */
class CommentListParceler(val comments: List<Api.Comment>) : Freezable {

    override fun freeze(sink: Freezable.Sink) = with(sink) {
        writeValues(comments) { comment ->
            writeLong(comment.id)
            writeLong(comment.parent)
            writeFloat(comment.confidence)
            writeShort(comment.up)
            writeShort(comment.down)
            writeByte(comment.mark)
            write(comment.created)
            writeString(comment.name)
            writeString(comment.content)
        }
    }

    companion object : Unfreezable<CommentListParceler> {
        @JvmField
        val CREATOR = parcelableCreator()

        override fun unfreeze(source: Freezable.Source): CommentListParceler {
            val comments = source.readValuesIndexed { idx ->
                Api.Comment(
                        id = source.readLong(),
                        parent = source.readLong(),
                        confidence = source.readFloat(),
                        up = source.readShort().toInt(),
                        down = source.readShort().toInt(),
                        mark = source.readByte().toInt(),
                        created = source.read(Instant),
                        name = source.readString(),
                        content = source.readString())
            }

            return CommentListParceler(comments)
        }
    }

}