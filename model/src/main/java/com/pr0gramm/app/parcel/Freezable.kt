package com.pr0gramm.app.parcel

import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import com.pr0gramm.app.Logger
import com.pr0gramm.app.Stopwatch
import com.pr0gramm.app.listOfSize
import com.pr0gramm.app.time
import okio.*
import java.util.zip.Deflater
import java.util.zip.Inflater
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
            var rest = value shl 1 xor (value shr 31)

            while (rest and -0x80 != 0) {
                writeByte(rest and 0x7F or 0x80)
                rest = rest ushr 7
            }

            writeByte(rest and 0x7F)
        }

        fun writeLong(value: Long) {
            var rest = value shl 1 xor (value shr 63)

            while (rest and -0x80L != 0L) {
                writeByte(rest.toInt() and 0x7F or 0x80)
                rest = rest ushr 7
            }

            writeByte(rest.toInt() and 0x7F)
        }

        fun writeFloat(f: Float) {
            sink.writeInt(f.toBits())
        }

        fun writeString(s: String) {
            val bytes = ByteString.encodeUtf8(s)
            writeInt(bytes.size())

            if (bytes.size() > 0) {
                sink.write(bytes)
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
            var value = 0
            var len = 0

            do {
                val b = readByte().toInt()
                if (b and 0x80 == 0) {
                    val raw = value or (b shl len)
                    val temp = raw shl 31 shr 31 xor raw shr 1
                    return temp xor (raw and (1 shl 31))
                }

                value = value or (b and 0x7F shl len)
                len += 7
            } while (len <= 35)

            throw IllegalArgumentException("Variable length quantity is too long")
        }

        fun readLong(): Long {
            var value = 0L
            var len = 0

            do {
                val b = readByte().toLong()
                if (b and 0x80L == 0L) {
                    val raw = value or (b shl len)
                    val temp = raw shl 63 shr 63 xor raw shr 1
                    return temp xor (raw and (1L shl 63))
                }

                value = value or (b and 0x7F shl len)
                len += 7
            } while (len <= 63)

            throw IllegalArgumentException("Variable length quantity is too long")
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

        // assume that we might not need to compress and
        // start with a zero byte to indicate that later.
        raw.writeByte(0)

        // freeze it to the raw buffer
        f.freeze(Freezable.Sink(raw))

        if (raw.size() < 64) {
            // no compression needed
            return raw.readByteArray()

        } else {
            val watch = Stopwatch()
            val uncompressedSize = raw.size()

            // assumption of not needing compression failed, skipping zero byte.
            raw.skip(1)

            val buffer = Buffer()
            buffer.writeByte(1)

            DeflaterSink(buffer, Deflater(6)).use { sink ->
                sink.write(raw, raw.size())
            }

            logger.debug {
                "Compressed %d bytes to %d (%1.2f%%) took %s".format(
                        uncompressedSize, buffer.size(),
                        buffer.size() * 100.0 / uncompressedSize, watch)
            }

            return buffer.readByteArray()
        }
    }

    fun <T : Freezable> unfreeze(data: ByteArray, c: Unfreezable<T>): T {
        return logger.time("Unfreezing object of type ${c.javaClass.enclosingClass?.directName}") {
            val notCompressed = data[0] == 0.toByte()

            val rawSource = bufferOf(data, 1, data.size - 1)
            if (notCompressed) {
                c.unfreeze(Freezable.Source(rawSource))

            } else {
                InflaterSource(rawSource, Inflater()).use { source ->
                    c.unfreeze(Freezable.Source(Okio.buffer(source)))
                }
            }
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun bufferOf(data: ByteArray, offset: Int = 0, length: Int = data.size): Buffer {
    return Buffer().apply { write(data, offset, length) }
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

private inline val Class<*>.directName: String
    get() {
        return name.takeLastWhile { it != '.' }.replace('$', '.')
    }


fun <F : Freezable> Freezable.Sink.writeValues(values: Collection<F>) {
    writeInt(values.size)
    values.forEach { write(it) }
}

inline fun Freezable.Sink.writeValues(n: Int, fn: (idx: Int) -> Unit) {
    contract { callsInPlace(fn) }

    writeInt(n)
    repeat(n, fn)
}

fun <F : Freezable> Freezable.Source.readValues(c: Unfreezable<F>): List<F> {
    return listOfSize(readInt()) { read(c) }
}

inline fun <E> Freezable.Source.readValues(fn: () -> E): List<E> {
    return listOfSize(readInt()) { fn() }
}
