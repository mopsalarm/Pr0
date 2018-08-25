package com.pr0gramm.app.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Parcelable
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.support.v4.util.LongSparseArray
import android.support.v4.view.PagerAdapter
import android.view.View
import android.view.ViewGroup
import com.pr0gramm.app.util.logger
import java.util.*

/**
 * This implementation has a [.getItemId] to identify items
 * and fragments, even if they change places between adapter updates.

 * @see android.support.v4.app.FragmentStatePagerAdapter
 */
abstract class IdFragmentStatePagerAdapter(private val mFragmentManager: FragmentManager) : PagerAdapter() {

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
        return mFragments.get(itemId)
    }

    @SuppressLint("CommitTransaction")
    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val id = getItemId(position)

        // If we already have this item instantiated, there is nothing
        // to do.  This can happen when we are restoring the entire pager
        // from its saved state, where the fragment manager has already
        // taken care of restoring the fragments we previously had instantiated.
        val f = mFragments.get(id)
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
            mSavedState.put(id, mFragmentManager.saveFragmentInstanceState(fragment))
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

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return (`object` as Fragment).view === view
    }

    override fun saveState(): Parcelable? {
        var state: Bundle? = null
        if (mSavedState.size > 0) {
            state = Bundle()

            val ids = LongArray(mSavedState.size)
            val states = arrayOfNulls<Parcelable>(mSavedState.size)

            mSavedState.entries.forEachIndexed { idx, entry ->
                ids[idx] = entry.key
                states[idx] = entry.value
            }

            state.putLongArray("ids", ids)
            state.putParcelableArray("states", states)
        }

        for (idx in 0..mFragments.size() - 1) {
            val f = mFragments.valueAt(idx)
            if (f != null) {
                state = state ?: Bundle()
                val key = "f" + mFragments.keyAt(idx)

                try {
                    mFragmentManager.putFragment(state, key, f)
                } catch(err: IllegalStateException) {
                    logger.info("Could not put fragment into the bundle. Skipping.", err)
                }
            }
        }
        return state
    }

    override fun restoreState(state: Parcelable?, loader: ClassLoader?) {
        if (state is Bundle) {
            state.classLoader = loader

            val ids = state.getLongArray("ids")
            val fss = state.getParcelableArray("states")

            mSavedState.clear()
            mFragments.clear()
            if (fss != null) {
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
                        logger.warn("Bad fragment at key " + key)
                    }
                }
            }
        }
    }

    companion object {
        private val logger = logger("IdFragmentStatePagerAdapter")
    }
}
