package com.pr0gramm.app;

import android.app.Activity;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import roboguice.fragment.RoboFragment;

/**
 * Workaround for a bug in support library version 20 and 21.
 *
 * @link http://ideaventure.blogspot.com.au/2014/10/nested-retained-fragment-lost-state.html
 */
public class NestingFragment extends RoboFragment {
    private FragmentManager retainedChildFragmentManager;

    public NestingFragment() {
        setRetainInstance(true);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        if (retainedChildFragmentManager != null) {
            //restore the last retained child fragment manager to the new
            //created fragment
            Log.i(getClass().getSimpleName(), "restoring child fragment manager");
            setFieldByName(this, "mChildFragmentManager", retainedChildFragmentManager);

            if (activity instanceof FragmentActivity) {
                // Support library wont set the correct activity on nested fragments.
                // thanks google.
                List<Fragment> fragments = new ArrayList<>(retainedChildFragmentManager.getFragments());
                for (Fragment fragment : fragments) {
                    if (fragment == null)
                        continue;

                    setFieldByName(fragment, "mActivity", activity);
                    fragment.onAttach(activity);
                }
            }
        }
    }

    private void setFieldByName(Fragment instance, String name, Object value) {
        try {
            Field childFmField = Fragment.class.getDeclaredField(name);
            childFmField.setAccessible(true);
            childFmField.set(instance, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        try {
            Log.i(getClass().getSimpleName(), "storing child fragment manager");
            Field childFmField = Fragment.class.getDeclaredField("mChildFragmentManager");
            childFmField.setAccessible(true);
            retainedChildFragmentManager = (FragmentManager) childFmField.get(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
