package com.pr0gramm.app.parcel

import android.os.Parcel
import android.os.Parcelable

fun <T : Parcelable> Parcel.readTypedList(creator: Parcelable.Creator<T>): List<T> {
    return mutableListOf<T>().apply { readTypedList(this, creator) }
}

fun <T : Parcelable> Parcel.readTyped(creator: Parcelable.Creator<T>): T {
    return creator.createFromParcel(this)
}

fun <T : Parcelable> Parcel.writeTyped(value: T) {
    value.writeToParcel(this, 0)
}

fun <T : Enum<T>> Parcel.writeEnum(value: T) {
    writeInt(value.ordinal)
}

inline fun <reified T : Enum<T>> Parcel.readEnum(): T? {
    val values = enumValues<T>()
    return values.getOrNull(readInt())
}

inline fun <reified T : Parcelable> creator(crossinline createFromParcel: (Parcel) -> T): Parcelable.Creator<T> {
    return object : Parcelable.Creator<T> {
        override fun createFromParcel(source: Parcel): T = createFromParcel(source)
        override fun newArray(size: Int): Array<T?> = arrayOfNulls(size)
    }
}
