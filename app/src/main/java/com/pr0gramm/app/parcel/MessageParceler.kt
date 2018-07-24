package com.pr0gramm.app.parcel

import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Api

/**
 */
class MessageParceler(val message: Api.Message) : Freezable {
    override fun freeze(sink: Freezable.Sink) = with(sink) {
        writeLong(message.id)
        writeLong(message.itemId)
        writeInt(message.mark)
        writeString(message.message)
        writeString(message.name)
        writeInt(message.score)
        writeInt(message.senderId)
        write(message.creationTime)
        writeString(message.thumbnail ?: "")
    }

    companion object : Unfreezable<MessageParceler> {
        @JvmField
        val CREATOR = parcelableCreator()

        override fun unfreeze(source: Freezable.Source): MessageParceler = with(source) {
            MessageParceler(Api.Message(
                    id = readLong(),
                    itemId = readLong(),
                    mark = readInt(),
                    message = readString(),
                    name = readString(),
                    score = readInt(),
                    senderId = readInt(),
                    creationTime = read(Instant),
                    thumbnail = readString().takeIf { it.isNotEmpty() }))
        }
    }
}
