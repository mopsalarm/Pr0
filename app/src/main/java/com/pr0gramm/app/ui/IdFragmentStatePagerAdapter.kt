package com.pr0gramm.app.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.viewpager.widget.PagerAdapter
import com.pr0gramm.app.util.Logger
import com.pr0gramm.app.util.LongSparseArray
import java.util.*

/**
 * This implementation has a [.getItemId] to identify items
 * and fragments, even if they change places between adapter updates.

 * @see androidx.fragment.app.FragmentStatePagerAdapter
 */
abstract class IdFragmentStatePagerAdapter(private val mFragmentManager: FragmentManager) : PagerAdapter() {
    private val logger = Logger("IdFragmentStatePagerAdapter")

    // we only cache the most recent few saved states.
    private val mSavedState = object : LinkedHashMap<Long, Fragment.SavedState>() {
        override fun removeEldestEntry(eldest: Map.Entry<Long, Fragment.SavedState>): Boolean {
            return size > 5
        }
    }

    private val mFragments = LongSparseArray<Fragment>()
    private var mCurTransaction: FragmentTransaction? = null
    private var mCurrentPrimaryItem: Fragment? = null

    /**
     * Return the Fragment associated with a specified position.
     */
    abstract fun getItem(position: Int): Fragment

    override fun startUpdate(container: ViewGroup) {
        if (container.id == View.NO_ID) {
            throw IllegalStateException("ViewPager with adapter ${this} requires a view id")
        }
    }

    /**
     * Gets the fragment for the given position, if one already exists.

     * @param position The position of the fragment to get
     */
    fun getFragment(position: Int): Fragment? {
        val itemId = getItemId(position)
        return mFragments[itemId]
    }

    @SuppressLint("CommitTransaction")
    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val id = getItemId(position)

        // If we already have this item instantiated, there is nothing
        // to do.  This can happen when we are restoring the entire pager
        // from its saved state, where the fragment manager has already
        // taken care of restoring the fragments we previously had instantiated.
        val f = mFragments[id]
        if (f != null) {
            return f
        }

        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction()
        }

        val fragment = getItem(position)
        val fss = mSavedState[id]
        if (fss != null) {
            fragment.setInitialSavedState(fss)
        }

        fragment.setMenuVisibility(false)
        fragment.userVisibleHint = false
        mFragments.put(id, fragment)
        mCurTransaction?.add(container.id, fragment)

        return fragment
    }

    protected abstract fun getItemId(position: Int): Long

    @SuppressLint("CommitTransaction")
    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        val fragment = `object` as Fragment

        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction()
        }

        try {
            val id = getItemId(position)
            mFragments.remove(id)

            mSavedState.remove(id)
            mFragmentManager.saveFragmentInstanceState(fragment)?.let { state ->
                mSavedState.put(id, state)
            }
        } catch (ignored: Exception) {
            // looks like this sometimes happen during save if the fragment is not in the
            // fragment manager. We will ignore it.
        }

        mCurTransaction?.remove(fragment)
    }

    override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {
        val fragment = `object` as Fragment
        if (fragment !== mCurrentPrimaryItem) {
            mCurrentPrimaryItem?.let {
                it.setMenuVisibility(false)
                it.userVisibleHint = false
            }

            try {
                fragment.setMenuVisibility(true)
                fragment.userVisibleHint = true
            } catch (ignored: NullPointerException) {
                // another workaround:
                // support library might access a fragment manager that is null.
            }

            mCurrentPrimaryItem = fragment
        }
    }

    override fun finishUpdate(container: ViewGroup) {
        try {
            mCurTransaction?.commitNowAllowingStateLoss()
            mCurTransaction = null
        } catch (ignored: RuntimeException) {
            // Sometimes we get a "activity has been destroyed." exception.
        }
    }

    override fun isViewFromObject(view: View, obj: Any): Boolean {
        return (obj as Fragment).view === view
    }

    override fun saveState(): Parcelable? {
        var state: Bundle? = null

        if (mSavedState.size > 0) {
            val ids = LongArray(mSavedState.size)
            val states = arrayOfNulls<Parcelable>(mSavedState.size)

            mSavedState.entries.forEachIndexed { idx, entry ->
                ids[idx] = entry.key
                states[idx] = entry.value
            }

            state = Bundle()
            state.putLongArray("ids", ids)
            state.putParcelableArray("states", states)
        }

        for (idx in 0 until mFragments.size) {
            state = state ?: Bundle()
            val key = "f" + mFragments.keyAt(idx)

            try {
                mFragmentManager.putFragment(state, key, mFragments.valueAt(idx))
            } catch (err: IllegalStateException) {
                logger.warn("Could not put fragment into the bundle. Skipping.", err)
            }
        }

        return state
    }

    override fun restoreState(state: Parcelable?, loader: ClassLoader?) {
        if (state is Bundle) {
            state.classLoader = loader

            val ids = state.getLongArray("ids")
            val fss = state.getParcelableArray("states")

            mFragments.clear()
            mSavedState.clear()

            if (ids != null && fss != null) {
                for (i in fss.indices) {
                    mSavedState.put(ids[i], fss[i] as Fragment.SavedState)
                }
            }

            val keys = state.keySet()
            for (key in keys) {
                if (key.startsWith("f")) {
                    val id = java.lang.Long.parseLong(key.substring(1))
                    val f = mFragmentManager.getFragment(state, key)
                    if (f != null) {
                        f.setMenuVisibility(false)
                        mFragments.put(id, f)
                    } else {
                        logger.warn { "Bad fragment at key $key" }
                    }
                }
            }
        }
    }
}
