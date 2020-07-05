package com.pr0gramm.app.parcel

import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import com.pr0gramm.app.Logger
import com.pr0gramm.app.listOfSize
import com.pr0gramm.app.time
import okio.*
import java.io.ByteArrayInputStream
import kotlin.contracts.contract


private val logger = Logger("Freezer")

/**
 * Very simple re-implementation of the Parcelable framework.
 */
interface Freezable : Parcelable {
    fun freeze(sink: Sink)

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        logger.debug { "Lazy parceling of ${this.javaClass.directName}" }
        dest.writeByteArray(Freezer.freeze(this))
    }

    class Sink(private val sink: BufferedSink) {
        fun write(f: Freezable) {
            f.freeze(this)
        }

        fun writeBoolean(value: Boolean) {
            sink.writeByte(if (value) 1 else 0)
        }

        fun writeByte(value: Int) {
            sink.writeByte(value)
        }

        fun writeShort(value: Int) {
            writeInt(value)
        }

        fun writeInt(value: Int) {
            sink.writeInt(value)
        }

        fun writeLong(value: Long) {
            sink.writeLong(value)
        }

        fun writeFloat(f: Float) {
            sink.writeInt(f.toBits())
        }

        fun writeString(s: String) {
            val size = s.utf8Size()
            writeInt(size.toInt())

            if (size > 0) {
                sink.writeUtf8(s)
            }
        }
    }

    class Source(private val source: BufferedSource) {
        fun <F : Freezable> read(c: Unfreezable<F>): F {
            return c.unfreeze(this)
        }

        fun readBoolean(): Boolean = source.readByte() != 0.toByte()

        fun readByte(): Byte = source.readByte()

        fun readShort(): Short = readInt().toShort()

        fun readInt(): Int {
            return source.readInt()
        }

        fun readLong(): Long {
            return source.readLong()
        }

        fun readFloat(): Float {
            return Float.fromBits(source.readInt())
        }

        fun readString(): String {
            val size = readInt()
            if (size == 0) {
                return ""
            }

            return source.readUtf8(size.toLong())
        }
    }
}

object Freezer {
    fun freeze(f: Freezable): ByteArray = logger.time("Freezing object of type ${f.javaClass.directName}") {
        val raw = Buffer()

        // freeze it to the raw buffer
        f.freeze(Freezable.Sink(raw))

        return raw.readByteArray()
    }

    fun <T : Freezable> unfreeze(data: ByteArray, c: Unfreezable<T>): T {
        return logger.time("Unfreezing object of type ${c.javaClass.enclosingClass?.directName}") {
            c.unfreeze(Freezable.Source(ByteArrayInputStream(data).source().buffer()))
        }
    }
}

interface Unfreezable<T : Freezable> {
    fun unfreeze(source: Freezable.Source): T
}

inline fun <reified T : Freezable> Unfreezable<T>.parcelableCreator() = object : Parcelable.Creator<T> {
    override fun createFromParcel(source: Parcel): T {
        return Freezer.unfreeze(source.createByteArray()!!, this@parcelableCreator)
    }

    override fun newArray(size: Int): Array<T?> = arrayOfNulls(size)
}

fun Bundle.putFreezable(key: String, f: Freezable) {
    putParcelable(key, f)
}

fun <T : Freezable> Bundle.getFreezableOrNull(key: String, c: Unfreezable<T>): T? {
    return getParcelable(key)
}

fun <T : Freezable> Bundle.getFreezable(key: String, c: Unfreezable<T>): T {
    return getFreezableOrNull(key, c)
            ?: throw IllegalArgumentException("No freezable found for '$key'")
}


fun <T : Freezable> Bundle.getParcelable(key: String, c: Unfreezable<T>): T? {
    return getParcelable(key)
}

fun <T : Freezable> Intent.getFreezableExtra(key: String, c: Unfreezable<T>): T? {
    return getParcelableExtra(key)
}

private inline val Class<*>.directName: String
    get() {
        return name.takeLastWhile { it != '.' }.replace('$', '.')
    }


fun <F : Freezable> Freezable.Sink.writeValues(values: Collection<F>) {
    writeInt(values.size)
    values.forEach { write(it) }
}

inline fun <E> Freezable.Sink.writeValues(values: List<E>, fn: (value: E) -> Unit) {
    contract { callsInPlace(fn) }

    writeInt(values.size)
    values.forEach(fn)
}

fun <F : Freezable> Freezable.Source.readValues(c: Unfreezable<F>): List<F> {
    return listOfSize(readInt()) { read(c) }
}

inline fun <E> Freezable.Source.readValues(fn: () -> E): List<E> {
    return listOfSize(readInt()) { fn() }
}

inline fun <E> Freezable.Source.readValuesIndexed(fn: (idx: Int) -> E): List<E> {
    return listOfSize(readInt()) { fn(it) }
}
