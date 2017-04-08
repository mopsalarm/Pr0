package com.pr0gramm.app.parcel

import android.os.Parcel
import android.os.Parcelable

/**
 */
class LambdaCreator<T : Parcelable>(
        private val fnCreateFromParcel: (Parcel) -> T,
        private val fnNewArray: (Int) -> Array<T>) : Parcelable.Creator<T> {

    override fun createFromParcel(source: Parcel): T {
        return fnCreateFromParcel(source)
    }

    override fun newArray(size: Int): Array<T> {
        return fnNewArray(size)
    }
}
