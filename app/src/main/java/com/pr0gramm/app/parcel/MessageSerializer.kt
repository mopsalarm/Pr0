package com.pr0gramm.app.parcel

import android.os.Parcel
import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Message
import com.pr0gramm.app.api.pr0gramm.MessageType

/**
 */
class MessageSerializer(val message: Message) : DefaultParcelable {
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeByte(message.type.ordinal.toByte())
        dest.writeByte(message.flags.toByte())
        dest.writeLong(message.id)
        dest.writeLong(message.itemId)
        dest.writeInt(message.mark)
        dest.writeString(message.message)
        dest.writeString(message.name)
        dest.writeInt(message.score)
        dest.writeInt(message.senderId)
        dest.write(message.creationTime)
        dest.writeString(message.thumbnail ?: "")
        dest.writeString(message.image ?: "")
    }

    companion object CREATOR : SimpleCreator<MessageSerializer>(javaClassOf()) {
        override fun createFromParcel(source: Parcel): MessageSerializer = with(source) {
            MessageSerializer(Message(
                    read = true,
                    type = MessageType.values[readByte().toInt()],
                    flags = readByte().toInt(),
                    id = readLong(),
                    itemId = readLong(),
                    mark = readInt(),
                    message = readStringNotNull(),
                    name = readStringNotNull(),
                    score = readInt(),
                    senderId = readInt(),
                    creationTime = read(Instant),
                    thumbnail = readString()?.ifBlank { null },
                    image = readString()?.ifBlank { null }))
        }
    }
}
