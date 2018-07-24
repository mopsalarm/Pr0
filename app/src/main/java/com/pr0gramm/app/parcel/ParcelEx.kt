package com.pr0gramm.app.parcel

import android.os.Parcel
import android.os.Parcelable

inline fun <reified T : Parcelable> creator(crossinline createFromParcel: (Parcel) -> T): Parcelable.Creator<T> {
    return object : Parcelable.Creator<T> {
        override fun createFromParcel(source: Parcel): T = createFromParcel(source)
        override fun newArray(size: Int): Array<T?> = arrayOfNulls(size)
    }
}
