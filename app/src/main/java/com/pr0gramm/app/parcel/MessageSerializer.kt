package com.pr0gramm.app.parcel

import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Message
import com.pr0gramm.app.api.pr0gramm.MessageType

/**
 */
class MessageSerializer(val message: Message) : Freezable {
    override fun freeze(sink: Freezable.Sink) = with(sink) {
        writeByte(message.type.ordinal)
        writeByte(message.flags)
        writeLong(message.id)
        writeLong(message.itemId)
        writeInt(message.mark)
        writeString(message.message)
        writeString(message.name)
        writeInt(message.score)
        writeInt(message.senderId)
        write(message.creationTime)
        writeString(message.thumbnail ?: "")
        writeString(message.image ?: "")
    }

    companion object : Unfreezable<MessageSerializer> {
        @JvmField
        val CREATOR = parcelableCreator()

        override fun unfreeze(source: Freezable.Source): MessageSerializer = with(source) {
            MessageSerializer(Message(
                    read = true,
                    type = MessageType.values[readByte().toInt()],
                    flags = readByte().toInt(),
                    id = readLong(),
                    itemId = readLong(),
                    mark = readInt(),
                    message = readString(),
                    name = readString(),
                    score = readInt(),
                    senderId = readInt(),
                    creationTime = read(Instant),
                    thumbnail = readString().takeIf { it.isNotEmpty() },
                    image = readString().takeIf { it.isNotEmpty() }))
        }
    }
}
