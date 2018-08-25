package com.pr0gramm.app.ui.back

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentManager
import android.support.v4.app.SupportV4App
import com.pr0gramm.app.util.logger

object BackFragmentHelper {
    private val logger = logger("BackFragmentHelper")

    @JvmStatic
    fun dispatchOnBackAction(activity: FragmentActivity): Boolean {
        val fragments = listAllFragments(activity.supportFragmentManager)
        logger.debug("Got {} fragments to dispatch action to", fragments.size)

        for (fragment in fragments) {
            val handled = fragment.onBackButton()
            if (handled) {
                return true
            }
        }

        return false
    }

    private fun listAllFragments(fm: FragmentManager?): List<BackAwareFragment> {
        if (fm == null) {
            return emptyList()
        }

        val allFragments = fm.fragments ?: return emptyList()

        val fragments = mutableListOf<BackAwareFragment>()
        for (fragment in allFragments) {
            if (fragment == null) {
                continue
            }

            if (fragment.isVisible && fragment is BackAwareFragment) {
                fragments.add(fragment)
            }

            childFragmentManagerOf(fragment)?.let {
                fragments.addAll(listAllFragments(it))
            }
        }

        return fragments
    }

    private fun childFragmentManagerOf(fragment: Fragment): FragmentManager? {
        return SupportV4App.fragmentChildFragmentManager(fragment)
    }
}
