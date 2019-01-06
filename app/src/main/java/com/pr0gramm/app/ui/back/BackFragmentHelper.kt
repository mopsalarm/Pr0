package com.pr0gramm.app.ui.back

import androidx.fragment.app.SupportV4App
import com.pr0gramm.app.util.Logger

object BackFragmentHelper {
    private val logger = Logger("BackFragmentHelper")


    fun dispatchOnBackAction(activity: androidx.fragment.app.FragmentActivity): Boolean {
        val fragments = listAllFragments(activity.supportFragmentManager)
        logger.debug { "Got ${fragments.size} fragments to dispatch action to" }

        for (fragment in fragments) {
            val handled = fragment.onBackButton()
            if (handled) {
                return true
            }
        }

        return false
    }

    private fun listAllFragments(fm: androidx.fragment.app.FragmentManager?): List<BackAwareFragment> {
        if (fm == null) {
            return emptyList()
        }

        val allFragments = fm.fragments

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

    private fun childFragmentManagerOf(fragment: androidx.fragment.app.Fragment): androidx.fragment.app.FragmentManager? {
        return SupportV4App.fragmentChildFragmentManager(fragment)
    }
}
