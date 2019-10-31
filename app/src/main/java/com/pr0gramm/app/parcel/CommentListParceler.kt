package com.pr0gramm.app.parcel

import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Api

/**
 */
class CommentListParceler(val comments: List<Api.Comment>) : Freezable {

    override fun freeze(sink: Freezable.Sink) = with(sink) {
        // it is slightly more effective (in regards to space) to serialize all
        // comments & all names as one block before serializing the rest of the
        // comments as another block.
        writeValues(comments) { comment ->
            writeString(comment.name)
        }

        writeValues(comments) { comment ->
            writeString(comment.content)
        }

        writeValues(comments) { comment ->
            writeLong(comment.id)
            writeLong(comment.parent)
            writeFloat(comment.confidence)
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
            val names = source.readValues { source.readString() }
            val contents = source.readValues { source.readString() }

            val comments = source.readValuesIndexed { idx ->
                Api.Comment(
                        id = source.readLong(),
                        parent = source.readLong(),
                        name = names[idx],
                        content = contents[idx],
                        confidence = source.readFloat(),
                        up = source.readShort().toInt(),
                        down = source.readShort().toInt(),
                        mark = source.readByte().toInt(),
                        created = source.read(Instant))
            }

            return CommentListParceler(comments)
        }
    }

}