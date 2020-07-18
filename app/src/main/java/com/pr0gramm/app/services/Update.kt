package com.pr0gramm.app.services

import android.os.Parcel
import com.pr0gramm.app.model.update.UpdateModel
import com.pr0gramm.app.parcel.DefaultParcelable
import com.pr0gramm.app.parcel.SimpleCreator
import com.pr0gramm.app.parcel.readStringNotNull

/**
 * Update
 */
data class Update(val version: Int, val apk: String, val changelog: String) : DefaultParcelable {
    constructor(update: UpdateModel) : this(update.version, update.apk, update.changelog)

    val versionStr = "1.${version / 10}.${version % 10}"

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(version)
        dest.writeString(apk)
        dest.writeString(changelog)
    }

    companion object CREATOR : SimpleCreator<Update>() {
        override fun createFromParcel(source: Parcel): Update {
            return Update(
                    version = source.readInt(),
                    apk = source.readStringNotNull(),
                    changelog = source.readStringNotNull(),
            )
        }
    }
}
