package com.pr0gramm.app.util

import android.os.Bundle
import android.os.Parcelable
import androidx.fragment.app.Fragment
import java.util.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Eases the Fragment.newInstance ceremony by marking the fragment's args with this delegate
 * Just write the property in newInstance and read it like any other property after the fragment has been created
 *
 * Inspired by Jake Wharton, he mentioned it during his IO/17 talk about Kotlin
 */
private class FragmentArgumentDelegate<T : Any>(val nameOverride: String?, val defaultValue: T?) : ReadWriteProperty<Fragment, T> {
    @Suppress("UNCHECKED_CAST")
    override operator fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        val name = nameOverride ?: property.name
        val value = thisRef.arguments?.get(name) as T?
        return value ?: defaultValue ?: throw IllegalStateException("Property $name is not set")
    }

    override operator fun setValue(thisRef: Fragment, property: KProperty<*>, value: T) {
        val args = thisRef.arguments ?: Bundle().also { thisRef.arguments = it }
        setArgumentValue(args, nameOverride ?: property.name, value)
    }
}

private class OptionalFragmentArgumentDelegate<T : Any>(val nameOverride: String?, val default: T?) : ReadWriteProperty<Fragment, T?> {
    @Suppress("UNCHECKED_CAST")
    override operator fun getValue(thisRef: Fragment, property: KProperty<*>): T? {
        val name = nameOverride ?: property.name
        val value = thisRef.arguments?.get(name) as T?
        return value ?: default
    }

    override operator fun setValue(thisRef: Fragment, property: KProperty<*>, value: T?) {
        val args = thisRef.arguments ?: Bundle().also { thisRef.arguments = it }
        setArgumentValue(args, nameOverride ?: property.name, value)
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
        else -> throw IllegalStateException("Type ${value.javaClass.canonicalName} of property $key is not supported")
    }
}

fun <T : Any> fragmentArgument(name: String? = null): ReadWriteProperty<Fragment, T> {
    return FragmentArgumentDelegate(name, null)
}

fun <T : Any> fragmentArgumentWithDefault(defaultValue: T, name: String? = null): ReadWriteProperty<Fragment, T> {
    return FragmentArgumentDelegate(name, defaultValue)
}

fun <T : Any> optionalFragmentArgument(default: T? = null, name: String? = null): ReadWriteProperty<Fragment, T?> {
    return OptionalFragmentArgumentDelegate(name, default)
}

inline fun <reified T : Enum<T>> enumFragmentArgument(): ReadWriteProperty<Fragment, T> {
    val delegate = fragmentArgument<Int>()
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

inline fun <reified T : Enum<T>> optionalEnumFragmentArgument(): ReadWriteProperty<Fragment, T?> {
    val delegate = optionalFragmentArgument<Int>()
    val values = EnumSet.allOf(T::class.java).sortedBy { it.ordinal }.toTypedArray()

    return object : ReadWriteProperty<Fragment, T?> {
        override fun getValue(thisRef: Fragment, property: KProperty<*>): T? {
            return delegate.getValue(thisRef, property)?.let { idx -> values.getOrNull(idx) }
        }

        override fun setValue(thisRef: Fragment, property: KProperty<*>, value: T?) {
            delegate.setValue(thisRef, property, value?.ordinal)
        }
    }
}
