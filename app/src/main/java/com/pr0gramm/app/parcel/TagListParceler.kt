package com.pr0gramm.app.parcel

import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.util.listOfSize

/**
 */
class TagListParceler(val tags: List<Api.Tag>) : Freezable {
    override fun freeze(sink: Freezable.Sink) = with(sink) {
        writeInt(tags.size)

        tags.forEach { tag ->
            writeLong(tag.id)
            writeFloat(tag.confidence)
            writeString(tag.tag)
        }
    }

    companion object : Unfreezable<TagListParceler> {
        @JvmField
        val CREATOR = parcelableCreator()

        override fun unfreeze(source: Freezable.Source): TagListParceler = with(source) {
            val tags = listOfSize(readInt()) {
                val id = readLong()
                val confidence = readFloat()
                val tag = readString()
                Api.Tag(id, confidence, tag)
            }

            return TagListParceler(tags)
        }
    }
}
