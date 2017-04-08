package com.pr0gramm.app.parcel.core

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import com.google.common.base.Stopwatch
import com.google.common.base.Throwables
import com.google.common.reflect.TypeToken
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.GsonModule
import org.slf4j.LoggerFactory

/**
 */
abstract class Parceler<T> : Parcelable {

    val type: TypeToken<T> = object : TypeToken<T>(javaClass) {}

    val value: T?

    protected constructor(value: T) {
        this.value = value
    }

    protected constructor(parcel: Parcel) {
        // read binary data from the parcel
        val input = parcel.createByteArray()
        value = try {
            BinaryReader.from(input).use { reader ->
                GsonModule.INSTANCE.fromJson<T>(reader, type.type)
            }
        } catch (ioError: Exception) {
            Throwables.propagateIfPossible(ioError, RuntimeException::class.java)
            throw RuntimeException("Could not read gson as parcel", ioError)
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, f: Int) {
        val watch = Stopwatch.createStarted()

        val writer = BinaryWriter().use {
            GsonModule.INSTANCE.toJson(value, type.type, it)
            it
        }

        // now write serialized data to the parcel
        val output = writer.toByteArray()
        dest.writeByteArray(output)

        if (BuildConfig.DEBUG) {
            logger.info("writing of {} took {} ({} bytes)", type, watch, output.size)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("Parceler")

        fun <R, T : Parceler<R>> get(clazz: Class<T>, bundle: Bundle, key: String): R? {
            val wrapper = bundle.getParcelable<T>(key) ?: return null

            if (!clazz.isInstance(wrapper))
                throw IllegalArgumentException(String.format("Element %s is not of type %s", key, clazz))

            return wrapper.value
        }
    }
}
