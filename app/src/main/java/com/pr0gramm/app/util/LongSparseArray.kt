package com.pr0gramm.app.util

/**
 * SparseArray mapping longs to Objects, a version of the platform's
 * `android.util.LongSparseArray` that can be used on older versions of the
 * platform.  Unlike a normal array of Objects,
 * there can be gaps in the indices.  It is intended to be more memory efficient
 * than using a HashMap to map Longs to Objects, both because it avoids
 * auto-boxing keys and its data structure doesn't rely on an extra entry object
 * for each mapping.
 *
 *
 * Note that this container keeps its mappings in an array data structure,
 * using a binary search to find keys.  The implementation is not intended to be appropriate for
 * data structures
 * that may contain large numbers of items.  It is generally slower than a traditional
 * HashMap, since lookups require a binary search and adds and removes require inserting
 * and deleting entries in the array.  For containers holding up to hundreds of items,
 * the performance difference is not significant, less than 50%.
 *
 *
 * To help with performance, the container includes an optimization when removing
 * keys: instead of compacting its array immediately, it leaves the removed entry marked
 * as deleted.  The entry can then be re-used for the same key, or compacted later in
 * a single garbage collection step of all removed entries.  This garbage collection will
 * need to be performed at any time the array needs to be grown or the the map size or
 * entry values are retrieved.
 */
class LongSparseArray<E>(initialCapacity: Int = 10) : Cloneable {
    private var mGarbage = false

    private var mKeys: LongArray
    private var mValues: Array<Any?>
    private var mSize: Int = 0

    /**
     * Return true if size() is 0.
     * @return true if size() is 0.
     */
    val isEmpty: Boolean get() = size == 0

    init {
        if (initialCapacity == 0) {
            mKeys = longArrayOf()
            mValues = arrayOf()
        } else {
            val capacity = idealLongArraySize(initialCapacity)
            mKeys = LongArray(capacity)
            mValues = arrayOfNulls(capacity)
        }
    }

    public override fun clone(): LongSparseArray<E> {
        try {
            @Suppress("UNCHECKED_CAST")
            val clone = super.clone() as LongSparseArray<E>
            clone.mKeys = mKeys.clone()
            clone.mValues = mValues.clone()
            return clone
        } catch (e: CloneNotSupportedException) {
            throw AssertionError(e) // Cannot happen as we implement Cloneable.
        }
    }

    /**
     * Gets the Object mapped from the specified key, or `null`
     * if no such mapping has been made.
     */
    // See inline comment.
    operator fun get(key: Long): E? {
        return get(key, null)
    }

    /**
     * Gets the Object mapped from the specified key, or the specified Object
     * if no such mapping has been made.
     */
    @Suppress("UNCHECKED_CAST")
    operator fun get(key: Long, valueIfKeyNotFound: E?): E? {
        val i = mKeys.binarySearch(key, 0, mSize)

        return if (i < 0 || mValues[i] === DELETED) {
            valueIfKeyNotFound
        } else {
            mValues[i] as E
        }
    }

    /**
     * Removes the mapping from the specified key, if there was any.
     */
    fun delete(key: Long) {
        val i = mKeys.binarySearch(key, toIndex = mSize)
        if (i >= 0 && mValues[i] !== DELETED) {
            mValues[i] = DELETED
            mGarbage = true
        }
    }

    /**
     * Alias for [.delete].
     */
    fun remove(key: Long) {
        delete(key)
    }

    private fun gc() {
        val n = mSize
        var o = 0
        val keys = mKeys
        val values = mValues

        for (i in 0 until n) {
            val value = values[i]

            if (value !== DELETED) {
                if (i != o) {
                    keys[o] = keys[i]
                    values[o] = value
                    values[i] = null
                }

                o++
            }
        }

        mGarbage = false
        mSize = o
    }

    /**
     * Adds a mapping from the specified key to the specified value,
     * replacing the previous mapping from the specified key if there
     * was one.
     */
    fun put(key: Long, value: E) {
        var i = mKeys.binarySearch(key, toIndex = mSize)

        if (i >= 0) {
            mValues[i] = value
        } else {
            i = i.inv()

            if (i < mSize && mValues[i] === DELETED) {
                mKeys[i] = key
                mValues[i] = value
                return
            }

            if (mGarbage && mSize >= mKeys.size) {
                gc()

                // Search again because indices may have changed.
                i = mKeys.binarySearch(key, toIndex = mSize).inv()
            }

            if (mSize >= mKeys.size) {
                val n = idealLongArraySize(mSize + 1)

                val nkeys = LongArray(n)
                val nvalues = arrayOfNulls<Any>(n)

                System.arraycopy(mKeys, 0, nkeys, 0, mKeys.size)
                System.arraycopy(mValues, 0, nvalues, 0, mValues.size)

                mKeys = nkeys
                mValues = nvalues
            }

            if (mSize - i != 0) {
                System.arraycopy(mKeys, i, mKeys, i + 1, mSize - i)
                System.arraycopy(mValues, i, mValues, i + 1, mSize - i)
            }

            mKeys[i] = key
            mValues[i] = value
            mSize++
        }
    }

    /**
     * Copies all of the mappings from the `other` to this map. The effect of this call is
     * equivalent to that of calling [.put] on this map once for each mapping
     * from key to value in `other`.
     */
    fun putAll(rhs: LongSparseArray<out E>) {
        val lhs = this

        val lhsSize = lhs.size
        val rhsSize = rhs.size

        val targetSize = idealLongArraySize(lhsSize + rhsSize)

        var lhsIndex = 0
        var rhsIndex = 0
        var targetIndex = 0

        val keyArray = LongArray(targetSize)
        val valArray = arrayOfNulls<Any>(targetSize)

        while (lhsIndex < lhsSize && rhsIndex < rhsSize) {
            val lhsValue = lhs.mKeys[lhsIndex]
            val rhsValue = rhs.mKeys[rhsIndex]

            when {
                lhsValue < rhsValue -> {
                    keyArray[targetIndex] = lhsValue
                    valArray[targetIndex] = lhs.mValues[lhsIndex]
                    targetIndex++
                    lhsIndex++
                }

                lhsValue > rhsValue -> {
                    keyArray[targetIndex] = rhsValue
                    valArray[targetIndex] = rhs.mValues[rhsIndex]
                    targetIndex++
                    rhsIndex++
                }

                else -> {
                    keyArray[targetIndex] = rhsValue
                    valArray[targetIndex] = rhs.mValues[rhsIndex]
                    targetIndex++
                    lhsIndex++
                    rhsIndex++
                }
            }

        }

        // apply merged values
        mSize = targetIndex
        mKeys = keyArray
        mValues = valArray
        mGarbage = false
    }

    /**
     * Returns the number of key-value mappings that this LongSparseArray
     * currently stores.
     */
    val size: Int
        get() {
            if (mGarbage) {
                gc()
            }

            return mSize
        }

    /**
     * Given an index in the range `0...size()-1`, returns
     * the key from the `index`th key-value mapping that this
     * LongSparseArray stores.
     */
    fun keyAt(index: Int): Long {
        if (mGarbage) {
            gc()
        }

        return mKeys[index]
    }

    /**
     * Given an index in the range `0...size()-1`, returns
     * the value from the `index`th key-value mapping that this
     * LongSparseArray stores.
     */
    fun valueAt(index: Int): E {
        if (mGarbage) {
            gc()
        }

        return mValues[index] as E
    }

    /**
     * Given an index in the range `0...size()-1`, sets a new
     * value for the `index`th key-value mapping that this
     * LongSparseArray stores.
     */
    fun setValueAt(index: Int, value: E) {
        if (mGarbage) {
            gc()
        }

        mValues[index] = value
    }

    /**
     * Returns the index for which [.keyAt] would return the
     * specified key, or a negative number if the specified
     * key is not mapped.
     */
    fun indexOfKey(key: Long): Int {
        if (mGarbage) {
            gc()
        }

        return mKeys.binarySearch(key, toIndex = mSize)
    }

    /**
     * Returns an index for which [.valueAt] would return the
     * specified key, or a negative number if no keys map to the
     * specified value.
     * Beware that this is a linear search, unlike lookups by key,
     * and that multiple keys can map to the same value and this will
     * find only one of them.
     */
    fun indexOfValue(value: E): Int {
        if (mGarbage) {
            gc()
        }

        for (i in 0 until mSize)
            if (mValues[i] === value)
                return i

        return -1
    }

    operator fun contains(key: Long): Boolean {
        return indexOfKey(key) >= 0
    }

    /** Returns true if the specified key is mapped.  */
    fun containsKey(key: Long): Boolean {
        return indexOfKey(key) >= 0
    }

    /** Returns true if the specified value is mapped from any key.  */
    fun containsValue(value: E): Boolean {
        return indexOfValue(value) >= 0
    }

    /**
     * Removes all key-value mappings from this LongSparseArray.
     */
    fun clear() {
        val n = mSize
        val values = mValues

        for (i in 0 until n) {
            values[i] = null
        }

        mSize = 0
        mGarbage = false
    }

    /**
     * Puts a key/value pair into the array, optimizing for the case where
     * the key is greater than all existing keys in the array.
     */
    fun append(key: Long, value: E) {
        if (mSize != 0 && key <= mKeys[mSize - 1]) {
            put(key, value)
            return
        }

        if (mGarbage && mSize >= mKeys.size) {
            gc()
        }

        val pos = mSize
        if (pos >= mKeys.size) {
            val n = idealLongArraySize(pos + 1)

            val nkeys = LongArray(n)
            val nvalues = arrayOfNulls<Any>(n)

            // Log.e("SparseArray", "grow " + mKeys.length + " to " + n);
            System.arraycopy(mKeys, 0, nkeys, 0, mKeys.size)
            System.arraycopy(mValues, 0, nvalues, 0, mValues.size)

            mKeys = nkeys
            mValues = nvalues
        }

        mKeys[pos] = key
        mValues[pos] = value
        mSize = pos + 1
    }

    fun values(): Iterable<E> {
        return Iterable<E> {
            if (mGarbage) {
                gc()
            }

            ValueIterator<E>(mValues, mSize)
        }
    }

    override fun hashCode(): Int {
        if (mGarbage) {
            gc()
        }

        var hashCode = 0
        for (i in 0 until mSize) {
            hashCode = 31 * hashCode + (mKeys[i] xor mKeys[i].ushr(32)).toInt()
            hashCode = 31 * hashCode + mValues[i].hashCode()
        }

        return hashCode
    }

    override fun equals(other: Any?): Boolean {
        if (other !is LongSparseArray<*>) {
            return false
        }

        val size = this.size
        val otherSize = other.size
        if (size != otherSize)
            return false

        for (idx in 0 until size) {
            if (mKeys[idx] != other.mKeys[idx] || mValues[idx] != other.mValues[idx]) {
                return false
            }
        }

        return true
    }

    /**
     * {@inheritDoc}
     *
     *
     * This implementation composes a string by iterating over its mappings. If
     * this map contains itself as a value, the string "(this Map)"
     * will appear in its place.
     */
    override fun toString(): String {
        if (size <= 0) {
            return "{}"
        }

        val buffer = StringBuilder(mSize * 28)
        buffer.append('{')
        for (i in 0 until mSize) {
            if (i > 0) {
                buffer.append(", ")
            }
            val key = keyAt(i)
            buffer.append(key)
            buffer.append('=')
            val value = valueAt(i)
            if (value !== this) {
                buffer.append(value)
            } else {
                buffer.append("(this Map)")
            }
        }
        buffer.append('}')
        return buffer.toString()
    }
}

inline fun <T> longSparseArrayOf(values: Iterable<T>, crossinline keyOf: (T) -> Long): LongSparseArray<T> {
    val comparator = Comparator<T> { o1, o2 ->
        val x = keyOf(o1)
        val y = keyOf(o2)
        if (x < y) -1 else if (x == y) 0 else 1
    }

    val sortedValues = values.sortedWith(comparator)
    val sparse = LongSparseArray<T>(sortedValues.size)
    for (value in sortedValues)
        sparse.append(keyOf(value), value)

    return sparse
}

inline fun <E> LongSparseArray<E>.forEach(block: (Long, E) -> Unit) {
    val size = this.size

    for (idx in 0 until size) {
        block(keyAt(idx), valueAt(idx))
    }
}

private val DELETED = Any()

private fun idealLongArraySize(need: Int): Int {
    return idealByteArraySize(need * 8) / 8
}

private fun idealByteArraySize(need: Int): Int {
    for (i in 4..31)
        if (need <= (1 shl i) - 12)
            return (1 shl i) - 12

    return need
}

private class ValueIterator<E>(val values: Array<Any?>, val size: Int) : Iterator<E> {
    private var idx = 0

    override fun hasNext(): Boolean {
        return idx < size
    }

    @Suppress("UNCHECKED_CAST")
    override fun next(): E {
        val value = values[idx] as E
        idx++
        return value
    }
}