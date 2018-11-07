package com.pr0gramm.app.parcel

import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import com.pr0gramm.app.util.debug
import com.pr0gramm.app.util.logger
import com.pr0gramm.app.util.time
import okio.*
import java.io.ByteArrayInputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

private val logger = logger("Freezer")

/**
 * Very simple re-implementation of the Parcelable framework.
 */
interface Freezable : Parcelable {
    fun freeze(sink: Sink)

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        debug {
            logger.debug { "Lazy parceling of ${this.javaClass.simpleName}" }
        }

        dest.writeByteArray(Freezer.freeze(this))
    }

    class Sink(private val sink: BufferedSink) {
        fun write(f: Freezable) {
            f.freeze(this)
        }

        fun writeByte(value: Int) {
            sink.writeByte(value)
        }

        fun writeShort(value: Int) {
            sink.writeShort(value)
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
            val bytes = ByteString.encodeUtf8(s)
            sink.writeInt(bytes.size())

            if (bytes.size() > 0) {
                sink.write(bytes)
            }
        }
    }

    class Source(private val source: BufferedSource) {
        fun <F : Freezable> read(c: Unfreezable<F>): F {
            return c.unfreeze(this)
        }

        fun readByte(): Byte = source.readByte()
        fun readShort(): Short = source.readShort()
        fun readInt(): Int = source.readInt()
        fun readLong(): Long = source.readLong()

        fun readFloat(): Float {
            return Float.fromBits(source.readInt())
        }

        fun readString(): String {
            val size = source.readInt()
            if (size == 0) {
                return ""
            }

            return source.readUtf8(size.toLong())
        }
    }
}

object Freezer {
    fun freeze(f: Freezable): ByteArray {
        return logger.time({ "Freezing object of type ${f.javaClass.simpleName} (${it?.size} bytes)" }) {
            val buffer = Buffer()
            try {
                DeflaterSink(buffer, Deflater(Deflater.BEST_SPEED)).use {
                    Okio.buffer(it).use { bufferedSink ->
                        f.freeze(Freezable.Sink(bufferedSink))
                    }
                }

                return@time buffer.readByteArray()

            } finally {
                buffer.clear()
            }
        }
    }

    fun <T : Freezable> unfreeze(data: ByteArray, c: Unfreezable<T>): T {
        return logger.time("Unfreezing object of type ${c.javaClass.enclosingClass?.simpleName}") {
            val source = Okio.source(ByteArrayInputStream(data))

            InflaterSource(source, Inflater()).use { inflaterSource ->
                val bufferedSource = Okio.buffer(inflaterSource)
                c.unfreeze(Freezable.Source(bufferedSource))
            }
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

@Suppress("NOTHING_TO_INLINE")
inline fun Bundle.putFreezable(key: String, f: Freezable) {
    putParcelable(key, f)
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T : Freezable> Bundle.getFreezable(key: String, c: Unfreezable<T>): T? {
    return getParcelable(key)
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T : Freezable> Bundle.getParcelable(key: String, c: Unfreezable<T>): T? {
    return getParcelable(key)
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T : Freezable> Intent.getFreezableExtra(key: String, c: Unfreezable<T>): T? {
    return getParcelableExtra(key)
}
