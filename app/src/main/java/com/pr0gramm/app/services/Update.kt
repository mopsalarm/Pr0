package com.pr0gramm.app.services

import com.pr0gramm.app.model.update.UpdateModel
import com.pr0gramm.app.parcel.Freezable
import com.pr0gramm.app.parcel.Unfreezable
import com.pr0gramm.app.parcel.parcelableCreator

/**
 * Update
 */
data class Update(val version: Int, val apk: String, val changelog: String) : Freezable {
    constructor(update: UpdateModel) : this(update.version, update.apk, update.changelog)

    val versionStr: String
        get() {
            return "1.${version / 10}.${version % 10}"
        }

    override fun freeze(sink: Freezable.Sink): Unit = with(sink) {
        writeInt(version)
        writeString(apk)
        writeString(changelog)
    }

    companion object : Unfreezable<Update> {
        @JvmField
        val CREATOR = parcelableCreator()

        override fun unfreeze(source: Freezable.Source): Update = with(source) {
            return Update(version = readInt(), apk = readString(), changelog = readString())
        }
    }
}
