package com.pr0gramm.app.ui

import android.content.Context
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.util.AndroidUtility.checkMainThread
import com.pr0gramm.app.util.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.ArrayList

class Pagination<E : Any>(
        private val scope: CoroutineScope,
        private val loader: Loader<E>,
        initialState: State<E> = State.hasMoreState()) {

    private val logger = logger("Pagination(${loader.javaClass.simpleName})")

    var state: State<E> = initialState
        private set


    /**
     * Directly called with the current state if set.
     */
    var onStateChanged: (State<E>) -> Unit = {}

    /**
     * Loads the first page if no data is currently available
     */
    fun initialize() {
        val tailState = state.tailState
        if (tailState.hasMore && !tailState.loading && state.size == 0) {
            this.loadAtTail()
        }
    }

    /**
     * Modifies the paginations state using a custom function
     */
    fun updateState(state: State<E>) {
        checkMainThread()

        this.state = state

        // and publish updated state value
        onStateChanged(state)
    }

    fun hit(value: Any) {
        val state = this.state
        val headState = state.headState
        val tailState = state.tailState

        val idx = state.lookupTable[value] ?: return

        // check if we need to and are allowed to load at the head
        if (headState.hasMore && !headState.loading && idx < 12) {
            this.loadAtHead()
        }

        // check if we need to and are allowed to load at the tail
        if (tailState.hasMore && !tailState.loading && idx > state.size - 12) {
            this.loadAtTail()
        }
    }

    private fun loadAtHead() {
        logger.debug { "loadAtHead() was triggered" }
        load(loader::loadBefore) { state, function ->
            state.copy(headState = function(state.headState))
        }
    }

    private fun loadAtTail() {
        logger.debug { "loadAtTail() was triggered" }
        load(loader::loadAfter) { state, function ->
            state.copy(tailState = function(state.tailState))
        }
    }

    private fun load(
            loadCallback: suspend (previousValues: List<E>) -> StateTransform<E>,
            updateEndState: (State<E>, (EndState) -> (EndState)) -> State<E>) {

        scope.launch {
            // update state first
            updateState(updateEndState(state) { it.copy(loading = true, error = null) })

            val newState = try {
                logger.warn { "Start loading" }
                val updatedState = loadCallback(state.values)(state)

                logger.info { "Loading finished, updating state" }
                updateEndState(updatedState) { it.copy(loading = false, error = null) }

            } catch (err: CancellationException) {
                logger.warn { "Loading was canceled." }
                updateEndState(state) { it.copy(loading = false, error = null) }

            } catch (err: Exception) {
                logger.warn { "Loading failed" }
                updateEndState(state) { it.copy(loading = false, error = err) }
            }

            updateState(newState)
        }
    }

    data class State<E>(
            val values: List<E>,
            val headState: EndState = EndState(),
            val tailState: EndState = EndState()) {

        val size = values.size

        val lookupTable by lazy(LazyThreadSafetyMode.PUBLICATION) {
            IdentityHashMap<E, Int>(size).also { map ->
                values.forEachIndexed { index, value -> map[value] = index }
            }
        }

        companion object {
            fun <E> hasMoreState(values: List<E> = listOf()): State<E> {
                return State(values, tailState = EndState(hasMore = true))
            }
        }
    }

    data class EndState(
            val error: Exception? = null,
            val loading: Boolean = false,
            val hasMore: Boolean = false)

    abstract class Loader<E> {
        open suspend fun loadAfter(currentValues: List<E>): StateTransform<E> {
            return { state -> state.copy(tailState = EndState()) }
        }

        open suspend fun loadBefore(currentValues: List<E>): StateTransform<E> {
            return { state -> state.copy(headState = EndState()) }
        }
    }
}

typealias StateTransform<E> = (Pagination.State<E>) -> Pagination.State<E>


@Suppress("UNCHECKED_CAST")
abstract class PaginationRecyclerViewAdapter<P : Any, E : Any>(
        private val pagination: Pagination<P>,
        diffCallback: DiffUtil.ItemCallback<E>) : DelegateAdapter<E>(diffCallback) {

    private var initialized = false
    private var lookupTable = emptyMap<E, P>()

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        if (!initialized) {
            initialized = true

            updateAdapterValues(pagination.state)

            pagination.onStateChanged = this::updateAdapterValues
            pagination.initialize()
        }

        super.onAttachedToRecyclerView(recyclerView)
    }

    private fun updateAdapterValues(state: Pagination.State<P>) {
        val values = translateState(state)

        val adapterValues = ArrayList<E>(values.size)
        val lookupTable = IdentityHashMap<E, P>(values.size)

        values.forEach { value ->
            if (value is Translation<*, *>) {
                @Suppress("UNCHECKED_CAST")
                lookupTable[value.adapterValue as E] = value.stateValue as P

                adapterValues += value.adapterValue
            } else {
                adapterValues += value as E
            }
        }

        submitList(adapterValues)
    }

    abstract fun translateState(state: Pagination.State<P>): List<Any>

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        hitValue(position)
        super.onBindViewHolder(holder, position)
    }

    private fun hitValue(position: Int) {
        val stateValue = lookupTable[items[position]] ?: items[position] ?: return
        pagination.hit(stateValue)
    }

    class Translation<E : Any, P : Any>(val adapterValue: E, val stateValue: P)

    companion object {
        fun addEndStateToValues(context: Context, values: MutableList<in Any>, tailState: Pagination.EndState) {
            when {
                tailState.error != null ->
                    values += ErrorAdapterDelegate.errorValueOf(context, tailState.error)

                tailState.hasMore ->
                    values += Loading()
            }
        }
    }
}
