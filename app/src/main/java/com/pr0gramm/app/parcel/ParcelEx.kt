package com.pr0gramm.app.parcel

import android.os.Parcel
import android.os.Parcelable
import java.io.ByteArrayInputStream
import java.util.zip.DeflaterInputStream
import java.util.zip.InflaterInputStream

fun <T : Parcelable> Parcel.readTyped(creator: Parcelable.Creator<T>): T {
    return creator.createFromParcel(this)
}

fun <T : Parcelable> Parcel.writeTyped(value: T) {
    value.writeToParcel(this, 0)
}

inline fun parcelToByteArray(builder: Parcel.() -> Unit): ByteArray {
    val parcel = Parcel.obtain()

    try {
        parcel.builder()

        // convert parcel to bytes and compress
        val bytes = parcel.marshall()
        return DeflaterInputStream(bytes.inputStream()).use { it.readBytes() }
    } finally {
        parcel.recycle()
    }
}

inline fun <R> byteArrayToParcel(bytes: ByteArray, consumer: (Parcel) -> R): R {
    // decompress bytes before using them
    val inflated = InflaterInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }

    val parcel = Parcel.obtain()
    try {
        parcel.unmarshall(inflated, 0, inflated.size);
        parcel.setDataPosition(0);

        return consumer(parcel)

    } finally {
        parcel.recycle()
    }
}


