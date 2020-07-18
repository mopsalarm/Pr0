package com.pr0gramm.app.parcel

import android.os.Parcel
import android.os.Parcelable
import com.pr0gramm.app.listOfSize
import java.lang.reflect.ParameterizedType

inline fun <reified T : Parcelable> creator(crossinline createFromParcel: (Parcel) -> T): Parcelable.Creator<T> {
    return object : Parcelable.Creator<T> {
        override fun createFromParcel(source: Parcel): T = createFromParcel(source)
        override fun newArray(size: Int): Array<T?> = arrayOfNulls(size)
    }
}


interface DefaultParcelable : Parcelable {
    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int)
}

fun Parcel.readStringNotNull(): String {
    return readString()!!
}

fun Parcel.write(value: Parcelable) {
    value.writeToParcel(this, 0)
}

fun <T : Parcelable> Parcel.read(c: Parcelable.Creator<T>): T {
    return c.createFromParcel(this)
}

fun Parcel.writeValues(values: Collection<Parcelable>) {
    writeValues(values) { write(it) }
}

inline fun <T> Parcel.writeValues(values: Collection<T>, write: Parcel.(T) -> Unit) {
    writeInt(values.size)
    for (value in values) {
        write(value)
    }
}

fun <T : Parcelable> Parcel.readValues(c: Parcelable.Creator<T>): List<T> {
    return listOfSize(readInt()) { read(c) }
}

inline fun <T> Parcel.readValues(readValue: Parcel.(idx: Int) -> T): List<T> {
    return listOfSize(readInt()) { readValue(it) }
}

fun Parcel.writeBooleanCompat(value: Boolean) {
    writeInt(if (value) 1 else 0)
}

fun Parcel.readBooleanCompat(): Boolean {
    return readInt() != 0
}


abstract class SimpleCreator<T : Parcelable> : Parcelable.Creator<T> {
    @Suppress("UNCHECKED_CAST")
    private val type = (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<T>

    override fun newArray(size: Int): Array<T> {
        @Suppress("UNCHECKED_CAST")
        return java.lang.reflect.Array.newInstance(type, size) as Array<T>
    }

    abstract override fun createFromParcel(source: Parcel): T
}

open class ConstructorCreator<T : Parcelable>(private val constructor: (Parcel) -> T) : SimpleCreator<T>() {
    override fun createFromParcel(source: Parcel): T {
        return constructor(source)
    }
}

