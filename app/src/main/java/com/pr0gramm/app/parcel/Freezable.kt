package com.pr0gramm.app.parcel

import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import com.pr0gramm.app.util.*
import okio.*
import java.io.ByteArrayInputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

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

        fun <F : Freezable> writeValues(values: Collection<F>) {
            writeInt(values.size)
            values.forEach { write(it) }
        }
    }

    class Source(private val source: BufferedSource) {
        fun <F : Freezable> read(c: Unfreezable<F>): F {
            return c.unfreeze(this)
        }

        fun readBoolean(): Boolean = source.readByte() != 0.toByte()

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

        fun <F : Freezable> readValues(c: Unfreezable<F>): List<F> {
            return listOfSize(readInt()) { read(c) }
        }
    }
}

object Freezer {
    fun freeze(f: Freezable): ByteArray {
        return logger.timeSuffix("Freezing object of type ${f.javaClass.directName}") {
            val raw = Buffer()
            val buffer = Buffer()
            try {
                // freeze it to the raw buffer
                f.freeze(Freezable.Sink(raw))

                // select compression
                val comp = when {
                    raw.size() < 16 * 1024 -> Deflater.NO_COMPRESSION
                    raw.size() < 64 * 1024 -> Deflater.BEST_SPEED
                    else -> Deflater.BEST_COMPRESSION
                }

                if (comp == Deflater.NO_COMPRESSION) {
                    // identify as uncompressed
                    buffer.writeByte(0)
                    buffer.write(raw, raw.size())

                } else {
                    // identify as compressed
                    buffer.writeByte(1)

                    // and write data compressed to the buffer
                    DeflaterSink(buffer, Deflater(comp)).use { def -> def.write(raw, raw.size()) }
                }


                return@timeSuffix Pair("${buffer.size()} bytes", buffer.readByteArray())

            } finally {
                raw.clear()
                buffer.clear()
            }
        }
    }

    fun <T : Freezable> unfreeze(data: ByteArray, c: Unfreezable<T>): T {
        return logger.time("Unfreezing object of type ${c.javaClass.enclosingClass?.directName}") {
            val source = Okio.source(ByteArrayInputStream(data, 1, data.size - 1))

            val notCompressed = data[0] == 0.toByte()
            if (notCompressed) {
                // directly read from the source
                Okio.buffer(source).use { bufferedSource ->
                    c.unfreeze(Freezable.Source(bufferedSource))
                }
            } else {
                // decompress
                InflaterSource(source, Inflater()).use { inflaterSource ->
                    Okio.buffer(inflaterSource).use { bufferedSource ->
                        c.unfreeze(Freezable.Source(bufferedSource))
                    }
                }
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
