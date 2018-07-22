package com.pr0gramm.app.parcel

import android.os.Parcel
import android.os.Parcelable
import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Api

/**
 */
class MessageParceler(val message: Api.Message) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.apply {
            writeLong(message.id)
            writeLong(message.itemId)
            writeInt(message.mark)
            writeString(message.message)
            writeString(message.name)
            writeInt(message.score)
            writeInt(message.senderId)
            writeTyped(message.creationTime)
            writeString(message.thumbnail)
        }
    }

    companion object {
        @JvmField
        val CREATOR = creator { parcel ->
            MessageParceler(Api.Message(
                    id = parcel.readLong(),
                    itemId = parcel.readLong(),
                    mark = parcel.readInt(),
                    message = parcel.readString(),
                    name = parcel.readString(),
                    score = parcel.readInt(),
                    senderId = parcel.readInt(),
                    creationTime = parcel.readTyped(Instant.CREATOR),
                    thumbnail = parcel.readString()))
        }
    }
}
