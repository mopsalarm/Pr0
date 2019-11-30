package com.pr0gramm.app.ui

import android.os.Parcel
import android.os.Parcelable
import android.util.SparseArray
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.R
import com.pr0gramm.app.parcel.creator
import com.pr0gramm.app.util.LongSparseArray
import com.pr0gramm.app.util.forEach
import com.pr0gramm.app.util.trace

class ViewHolderState() : Parcelable {
    private val states = LongSparseArray<ViewState>()

    /** Save the state of the view bound to the given holder.  */
    fun <VH> save(holder: VH)
            where VH : RecyclerView.ViewHolder,
                  VH : Aware {

        trace { "$this: Saving state for view ${holder.viewStateId}" }

        val state = states[holder.viewStateId] ?: ViewState()
        state.save(holder.itemView)
        states[holder.viewStateId] = state
    }

    /**
     * If a state was previously saved for this view holder via [.save] it will be restored
     * here.
     */
    fun <VH> restore(holder: VH)
            where VH : RecyclerView.ViewHolder,
                  VH : Aware {

        val state = states[holder.viewStateId]
        trace { "$this: Got state $state for view ${holder.viewStateId}" }
        if (state != null) {
            state.restore(holder.itemView)

        } else {
            // The first time a model is bound it won't have previous state. We need to make sure
            // the view is reset to its initial state to clear any changes from previously bound models
            holder.restoreInitialViewState()
        }
    }

    constructor(parcel: Parcel) : this() {
        repeat(parcel.readInt()) {
            val id = parcel.readLong()
            val value = parcel.readSparseArray<Parcelable>(javaClass.classLoader)

            if (id > 0 && value != null) {
                states.put(id, ViewState(value))
            }
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(states.size)
        states.forEach { id, viewState ->
            parcel.writeLong(id)
            parcel.writeSparseArray(viewState.state)
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object {
        @JvmField
        val CREATOR = creator { ViewHolderState(it) }
    }

    /**
     * A wrapper around a sparse array as a helper to save the state of a view. This also adds
     * parcelable support.
     */
    class ViewState(val state: SparseArray<Parcelable> = SparseArray()) {
        fun save(view: View) {
            val originalId = view.id
            setIdIfNoneExists(view)

            view.saveHierarchyState(state)
            view.id = originalId
        }

        fun restore(view: View) {
            val originalId = view.id
            setIdIfNoneExists(view)

            view.restoreHierarchyState(state)
            view.id = originalId
        }

        /**
         * If a view hasn't had an id set we need to set a temporary one in order to save state, since a
         * view won't save its state unless it has an id. The view's id is also the key into the sparse
         * array for its saved state, so the temporary one we choose just needs to be consistent between
         * saving and restoring state.
         */
        private fun setIdIfNoneExists(view: View) {
            if (view.id == View.NO_ID) {
                view.id = R.id.view_model_state_saving_id
            }
        }
    }

    interface Aware {
        val viewStateId: Long
        fun restoreInitialViewState()
    }
}