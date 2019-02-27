package com.pr0gramm.app


inline fun <T> listOfSize(n: Int, initializer: (Int) -> T): List<T> {
    val result = ArrayList<T>(n)
    for (idx in 0 until n) {
        result.add(initializer(idx))
    }

    return result
}

