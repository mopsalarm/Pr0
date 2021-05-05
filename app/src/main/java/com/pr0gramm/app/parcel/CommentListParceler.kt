package com.pr0gramm.app.parcel

import android.os.Parcel
import com.pr0gramm.app.Instant
import com.pr0gramm.app.Logger
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.listOfSize
import com.pr0gramm.app.time
import com.pr0gramm.app.util.Serde
import java.util.zip.Deflater

/**
 */
class CommentListParceler(val comments: List<Api.Comment>) : DefaultParcelable {
    private val logger = Logger("CommentListParceler")

    override fun writeToParcel(dest: Parcel, flags: Int) {
        val bytes = logger.time("Serialize comments to bytes") {
            Serde.serialize(level = Deflater.BEST_COMPRESSION) { out ->
                out.writeInt(comments.size)

                for (comment in comments) {
                    out.writeLong(comment.id)
                    out.writeLong(comment.parentId)
                    out.writeFloat(comment.confidence)
                    out.writeInt(comment.up)
                    out.writeInt(comment.down)
                    out.writeByte(comment.mark)
                    out.writeLong(comment.created.millis)
                    out.writeUTF(comment.name)

                    val bytes = comment.content.toByteArray()
                    out.writeInt(bytes.size)
                    out.write(bytes)
                }
            }
        }

        dest.writeByteArray(bytes)
    }

    companion object CREATOR : SimpleCreator<CommentListParceler>(javaClassOf()) {
        override fun createFromParcel(source: Parcel): CommentListParceler {
            val bytes = source.createByteArray() ?: return CommentListParceler(listOf())

            val comments = Serde.deserialize(bytes) { input ->
                listOfSize(input.readInt()) {
                    val id = input.readLong()
                    val parentId = input.readLong()
                    val confidence = input.readFloat()
                    val up = input.readInt()
                    val down = input.readInt()
                    val mark = input.readByte().toInt()
                    val created = Instant(input.readLong())
                    val name = input.readUTF()

                    val content = ByteArray(input.readInt()).apply { input.readFully(this) }
                    Api.Comment(
                            id = id,
                            parentId = parentId,
                            confidence = confidence,
                            up = up,
                            down = down,
                            mark = mark,
                            created = created,
                            name = name,
                            content = content.decodeToString(),
                    )
                }
            }

            return CommentListParceler(comments)
        }
    }
}