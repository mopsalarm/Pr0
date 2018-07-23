package com.pr0gramm.app.parcel

import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import com.pr0gramm.app.util.time
import okio.*
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Very simple re-implementation of the Parcelable framework.
 */
interface Freezable {
    fun freeze(sink: Sink)

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

    companion object {
        private val logger = LoggerFactory.getLogger("Freezer")
        fun freeze(f: Freezable): ByteArray {
            logger.time("Freezing object of type ${f.javaClass.simpleName}") {
                val buffer = Buffer()
                try {
                    DeflaterSink(buffer, Deflater(Deflater.BEST_SPEED)).use {
                        Okio.buffer(it).use { bufferedSink ->
                            f.freeze(Freezable.Sink(bufferedSink))
                        }
                    }

                    return buffer.readByteArray()

                } finally {
                    buffer.clear()
                }
            }
        }

        fun <T : Freezable> unfreeze(data: ByteArray, c: Unfreezable<T>): T {
            logger.time("Unfreezing object of type ${c.javaClass.enclosingClass?.simpleName}") {
                val source = Okio.source(ByteArrayInputStream(data))

                InflaterSource(source, Inflater()).use { inflaterSource ->
                    val bufferedSource = Okio.buffer(inflaterSource)
                    return c.unfreeze(Freezable.Source(bufferedSource))
                }
            }
        }
    }
}

interface Unfreezable<T : Freezable> {
    fun unfreeze(source: Freezable.Source): T
}

fun Parcel.writeFreezable(f: Freezable) {
    writeByteArray(Freezable.freeze(f))
}

fun <F : Freezable> Parcel.readFreezable(c: Unfreezable<F>): F {
    return Freezable.unfreeze(createByteArray(), c)
}


fun Bundle.putFreezable(key: String, f: Freezable) {
    putByteArray(key, Freezable.freeze(f))
}

fun <T : Freezable> Bundle.getFreezable(key: String, c: Unfreezable<T>): T? {
    return getByteArray(key)?.let { bytes -> Freezable.unfreeze(bytes, c) }
}


fun Intent.putExtra(key: String, f: Freezable) {
    val bytes = Freezable.freeze(f)
    putExtra(key, bytes)
}

fun <T : Freezable> Intent.getFreezableExtra(key: String, c: Unfreezable<T>): T? {
    return getByteArrayExtra(key)?.let { bytes -> Freezable.unfreeze(bytes, c) }
}