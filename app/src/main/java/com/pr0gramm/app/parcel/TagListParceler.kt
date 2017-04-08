package com.pr0gramm.app.parcel

import android.os.Parcel
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.parcel.core.Parceler
import com.pr0gramm.app.parcel.core.creator

/**
 */
class TagListParceler : Parceler<List<Api.Tag>> {
    constructor(values: List<Api.Tag>) : super(values) {}
    private constructor(parcel: Parcel) : super(parcel) {}

    companion object {
        @JvmField
        val CREATOR = creator(::TagListParceler)
    }
}
