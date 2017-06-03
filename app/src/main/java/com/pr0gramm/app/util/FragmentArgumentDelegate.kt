package com.pr0gramm.app.util

import android.os.Bundle
import android.os.Parcelable
import android.support.v4.app.Fragment
import java.util.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Eases the Fragment.newInstance ceremony by marking the fragment's args with this delegate
 * Just write the property in newInstance and read it like any other property after the fragment has been created
 *
 * Inspired by Jake Wharton, he mentioned it during his IO/17 talk about Kotlin
 */
private class FragmentArgumentDelegate<T : Any>(default: T? = null) : ReadWriteProperty<Fragment, T> {
    var value: T? = default

    override operator fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        if (value == null) {
            val args = thisRef.arguments ?: throw IllegalStateException("Cannot read property ${property.name} if no arguments have been set")
            @Suppress("UNCHECKED_CAST")
            value = args.get(property.name) as T
        }
        return value ?: throw IllegalStateException("Property ${property.name} could not be read")
    }

    override operator fun setValue(thisRef: Fragment, property: KProperty<*>, value: T) {
        if (thisRef.arguments == null) {
            thisRef.arguments = Bundle()
        }

        val args = thisRef.arguments
        setArgumentValue(args, property.name, value)
    }
}

private class OptionalFragmentArgumentDelegate<T : Any>(default: T? = null) : ReadWriteProperty<Fragment, T?> {
    var value: T? = default

    override operator fun getValue(thisRef: Fragment, property: KProperty<*>): T? {
        @Suppress("UNCHECKED_CAST")
        return thisRef.arguments?.get(property.name) as T?
    }

    override operator fun setValue(thisRef: Fragment, property: KProperty<*>, value: T?) {
        if (thisRef.arguments == null) {
            thisRef.arguments = Bundle()
        }

        val args = thisRef.arguments
        setArgumentValue(args, property.name, value)
    }
}

private fun setArgumentValue(args: Bundle, key: String, value: Any?) {
    if (value == null) {
        args.remove(key)
        return
    }

    when (value) {
        is String -> args.putString(key, value)
        is Int -> args.putInt(key, value)
        is Short -> args.putShort(key, value)
        is Long -> args.putLong(key, value)
        is Byte -> args.putByte(key, value)
        is ByteArray -> args.putByteArray(key, value)
        is Char -> args.putChar(key, value)
        is CharArray -> args.putCharArray(key, value)
        is CharSequence -> args.putCharSequence(key, value)
        is Float -> args.putFloat(key, value)
        is Double -> args.putDouble(key, value)
        is Bundle -> args.putBundle(key, value)
        is Parcelable -> args.putParcelable(key, value)
        else -> throw IllegalStateException("Type ${value.javaClass.canonicalName} of property ${key} is not supported")
    }
}

fun <T : Any> fragmentArgument(default: T? = null): ReadWriteProperty<Fragment, T> {
    return FragmentArgumentDelegate<T>(default)
}

fun <T : Any> optionalFragmentArgument(default: T? = null): ReadWriteProperty<Fragment, T?> {
    return OptionalFragmentArgumentDelegate<T>(default)
}

inline fun <reified T : Enum<T>> enumFragmentArgument(default: T? = null): ReadWriteProperty<Fragment, T> {
    val delegate = fragmentArgument<Int>(default?.ordinal)
    val values = EnumSet.allOf(T::class.java).sortedBy { it.ordinal }.toTypedArray()

    return object : ReadWriteProperty<Fragment, T> {
        override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
            val o = delegate.getValue(thisRef, property)
            return values[o]
        }

        override fun setValue(thisRef: Fragment, property: KProperty<*>, value: T) {
            delegate.setValue(thisRef, property, value.ordinal)
        }
    }
}
