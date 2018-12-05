package com.pr0gramm.app.ui

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.util.logger
import com.pr0gramm.app.util.observeChange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class Pagination<E>(private val scope: CoroutineScope, private val loader: Loader<E>, initialState: State<E>) {
    private val logger = logger("Pagination")

    private var state: State<E> by observeChange(initialState) {
        onStateChanged(state)
    }

    val currentState get() = state

    /**
     * Directly called with the current state if set.
     */
    var onStateChanged: (State<E>) -> Unit = {}

    /**
     * Loads the first page if no data is currently available
     */
    fun initialize() {
        val tailState = state.tailState
        if (tailState.hasMore && !tailState.loading && state.valueCount == 0) {
            this.loadAtTail()
        }
    }

    fun hit(idx: Int) {
        val state = this.state
        val headState = state.headState
        val tailState = state.tailState

        // check if we need to and are allowed to load at the head
        if (headState.hasMore && !headState.loading && idx < 12) {
            this.loadAtHead()
        }

        // check if we need to and are allowed to load at the tail
        if (tailState.hasMore && !tailState.loading && idx > state.valueCount - 12) {
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
            loadCallback: suspend (previousValue: E) -> StateTransform<E>,
            updateEndState: (State<E>, (EndState<E>) -> (EndState<E>)) -> State<E>) {

        // update state first
        state = updateEndState(state) { it.copy(loading = true, error = null) }

        scope.launch {
            try {
                logger.warn { "Start loading" }
                val updatedState = loadCallback(state.value)(state)

                logger.info { "Loading finished, updating state" }
                state = updateEndState(updatedState) { it.copy(loading = false, error = null) }

            } catch (err: CancellationException) {
                logger.warn { "Loading was canceled." }
                state = updateEndState(state) { it.copy(loading = false, error = null) }

            } catch (err: Exception) {
                logger.warn { "Loading failed" }
                state = updateEndState(state) { it.copy(loading = false, error = err) }
            }
        }
    }

    data class State<E>(
            val value: E,
            val valueCount: Int = 0,
            val headState: EndState<E> = EndState(),
            val tailState: EndState<E> = EndState()) {

        companion object {
            fun <E> hasMoreState(value: List<E> = listOf()): State<List<E>> {
                return hasMoreState(value, valueCount = value.size)
            }

            fun <E> hasMoreState(value: E, valueCount: Int): State<E> {
                return State(value, valueCount, tailState = EndState(hasMore = true))
            }
        }
    }

    data class EndState<E>(
            val error: Exception? = null,
            val loading: Boolean = false,
            val hasMore: Boolean = false)

    abstract class Loader<E> {
        open suspend fun loadAfter(currentValue: E): StateTransform<E> {
            return { state -> state.copy(tailState = EndState()) }
        }

        open suspend fun loadBefore(currentValue: E): StateTransform<E> {
            return { state -> state.copy(headState = EndState()) }
        }
    }
}

typealias StateTransform<E> = (Pagination.State<E>) -> Pagination.State<E>


abstract class PaginationRecyclerViewAdapter<S : Any, E : Any>(
        private val pagination: Pagination<S>,
        diffCallback: DiffUtil.ItemCallback<E>) : DelegateAdapter<E>(diffCallback) {

    /**
     * Initializes the connection to the pagination.
     */
    fun initialize() {
        updateAdapterValues(pagination.currentState)

        pagination.onStateChanged = this::updateAdapterValues
        pagination.initialize()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        // tell the pagination that we might need updates
        pagination.hit(position)

        return super.onBindViewHolder(holder, position)
    }

    abstract fun updateAdapterValues(state: Pagination.State<S>)
}
