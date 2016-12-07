package com.pr0gramm.app.ui.back;

import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.SupportV4App;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BackFragmentHelper {
    private static final Logger logger = LoggerFactory.getLogger("BackFragmentHelper");

    private BackFragmentHelper() {
    }

    public static boolean dispatchOnBackAction(FragmentActivity activity) {
        List<BackAwareFragment> fragments = listAllFragments(activity.getSupportFragmentManager());
        logger.debug("Got {} fragments to dispatch action to", fragments.size());

        for (BackAwareFragment fragment : fragments) {
            boolean handled = fragment.onBackButton();
            if (handled) {
                return true;
            }
        }

        return false;
    }

    private static List<BackAwareFragment> listAllFragments(FragmentManager fm) {
        if (fm == null) {
            return Collections.emptyList();
        }

        List<Fragment> allFragments = fm.getFragments();
        if (allFragments == null) {
            return Collections.emptyList();
        }

        List<BackAwareFragment> fragments = new ArrayList<>();
        for (Fragment fragment : allFragments) {
            if (fragment == null) {
                continue;
            }

            if (fragment.isVisible() && fragment instanceof BackAwareFragment) {
                fragments.add((BackAwareFragment) fragment);
            }

            FragmentManager cfm = childFragmentManagerOf(fragment);
            if (cfm != null) {
                fragments.addAll(listAllFragments(cfm));
            }
        }

        return fragments;
    }

    @Nullable
    private static FragmentManager childFragmentManagerOf(Fragment fragment) {
        return SupportV4App.fragmentChildFragmentManager(fragment);
    }
}
