package com.pr0gramm.app.parcel

import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import com.pr0gramm.app.Logger
import com.pr0gramm.app.Stopwatch
import com.pr0gramm.app.listOfSize
import com.pr0gramm.app.time
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.ByteString
import org.iq80.snappy.Snappy

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
            buffer.write(Snappy.compress(raw.readByteArray()))

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
            if (notCompressed) {
                val source = bufferOf(data, 1, data.size - 1)
                c.unfreeze(Freezable.Source(source))

            } else {
                val uncompressed = bufferOf(Snappy.uncompress(data, 1, data.size - 1))
                c.unfreeze(Freezable.Source(uncompressed))
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
