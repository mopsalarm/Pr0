package com.pr0gramm.app;

import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.util.LongSparseArray;
import android.support.v4.view.PagerAdapter;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.google.common.base.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This implementation has a {@link #getItemId(int)} to identify items
 * and fragments, even if they change places between adapter updates.
 *
 * @see android.support.v4.app.FragmentStatePagerAdapter
 */
public abstract class IdFragmentStatePagerAdapter extends PagerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(IdFragmentStatePagerAdapter.class);

    private static final String TAG = "MyFrgmntStatePgrAdapter";
    private static final boolean DEBUG = false;

    private final FragmentManager mFragmentManager;
    private FragmentTransaction mCurTransaction = null;

    private LongSparseArray<Fragment.SavedState> mSavedState = new LongSparseArray<>();
    private LongSparseArray<Fragment> mFragments = new LongSparseArray<>();
    private Fragment mCurrentPrimaryItem = null;

    public IdFragmentStatePagerAdapter(FragmentManager fm) {
        mFragmentManager = fm;
    }

    /**
     * Return the Fragment associated with a specified position.
     */
    public abstract Fragment getItem(int position);

    @Override
    public void startUpdate(ViewGroup container) {
    }

    /**
     * Gets the fragment for the given position, if one already exists.
     *
     * @param position The position of the fragment to get
     */
    public Optional<Fragment> getFragment(int position) {
        long itemId = getItemId(position);
        return Optional.fromNullable(mFragments.get(itemId));
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        long id = getItemId(position);

        // If we already have this item instantiated, there is nothing
        // to do.  This can happen when we are restoring the entire pager
        // from its saved state, where the fragment manager has already
        // taken care of restoring the fragments we previously had instantiated.
        Fragment f = mFragments.get(id);
        if (f != null) {
            return f;
        }

        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction();
        }

        Fragment fragment = getItem(position);
        if (DEBUG) Log.v(TAG, "Adding item #" + position + ": f=" + fragment);
        Fragment.SavedState fss = mSavedState.get(id);
        if (fss != null) {
            fragment.setInitialSavedState(fss);
        }

        fragment.setMenuVisibility(false);
        fragment.setUserVisibleHint(false);
        mFragments.put(id, fragment);
        mCurTransaction.add(container.getId(), fragment);

        return fragment;
    }

    protected abstract long getItemId(int position);

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        Fragment fragment = (Fragment) object;
        long id = getItemId(position);

        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction();
        }
        if (DEBUG) Log.v(TAG, "Removing item #" + position + ": f=" + object
                + " v=" + ((Fragment) object).getView());

        mSavedState.put(id, mFragmentManager.saveFragmentInstanceState(fragment));
        mFragments.remove(id);

        mCurTransaction.remove(fragment);
    }

    @Override
    public void setPrimaryItem(ViewGroup container, int position, Object object) {
        Fragment fragment = (Fragment) object;
        if (fragment != mCurrentPrimaryItem) {
            if (mCurrentPrimaryItem != null) {
                mCurrentPrimaryItem.setMenuVisibility(false);
                mCurrentPrimaryItem.setUserVisibleHint(false);
            }
            if (fragment != null) {
                fragment.setMenuVisibility(true);
                fragment.setUserVisibleHint(true);
            }
            mCurrentPrimaryItem = fragment;
        }
    }

    @Override
    public void finishUpdate(ViewGroup container) {
        if (mCurTransaction != null) {
            mCurTransaction.commitAllowingStateLoss();
            mCurTransaction = null;
            mFragmentManager.executePendingTransactions();
        }
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return ((Fragment) object).getView() == view;
    }

    @Override
    public Parcelable saveState() {
        Bundle state = null;
        if (mSavedState.size() > 0) {
            state = new Bundle();

            long[] ids = new long[mSavedState.size()];
            Parcelable[] states = new Parcelable[mSavedState.size()];
            for (int idx = 0; idx < mSavedState.size(); idx++) {
                ids[idx] = mSavedState.keyAt(idx);
                states[idx] = mSavedState.valueAt(idx);
            }
            state.putLongArray("ids", ids);
            state.putParcelableArray("states", states);
        }

        for (int idx = 0; idx < mFragments.size(); idx++) {
            Fragment f = mFragments.valueAt(idx);
            if (f != null) {
                if (state == null) {
                    state = new Bundle();
                }
                String key = "f" + mFragments.keyAt(idx);
                mFragmentManager.putFragment(state, key, f);
            }
        }
        return state;
    }

    @Override
    public void restoreState(Parcelable state, ClassLoader loader) {
        if (state != null) {
            Bundle bundle = (Bundle) state;
            bundle.setClassLoader(loader);

            long[] ids = bundle.getLongArray("ids");
            Parcelable[] fss = bundle.getParcelableArray("states");

            mSavedState.clear();
            mFragments.clear();
            if (fss != null) {
                for (int i = 0; i < fss.length; i++) {
                    mSavedState.put(ids[i], (Fragment.SavedState) fss[i]);
                }
            }

            Iterable<String> keys = bundle.keySet();
            for (String key : keys) {
                if (key.startsWith("f")) {
                    long id = Long.parseLong(key.substring(1));
                    Fragment f = mFragmentManager.getFragment(bundle, key);
                    if (f != null) {
                        f.setMenuVisibility(false);
                        mFragments.put(id, f);
                    } else {
                        logger.warn("Bad fragment at key " + key);
                    }
                }
            }
        }
    }
}
