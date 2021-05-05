package com.pr0gramm.app

class Stopwatch(private val startTime: Long = System.nanoTime()) {

    private val nanos get() = System.nanoTime() - startTime

    fun elapsed(): Duration = Duration(nanos)

    override fun toString(): String = Duration(nanos).toString()
}