package com.pr0gramm.app.ui.intro

import android.content.Context
import android.os.Bundle

import com.github.paolorotolo.appintro.AppIntroBaseFragment
import com.github.salomonbrys.kodein.KodeinInjector
import com.github.salomonbrys.kodein.android.SupportFragmentInjector
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.base.HasViewCache
import com.pr0gramm.app.ui.base.ViewCache

class GenericAppIntroFragment : AppIntroBaseFragment(), HasViewCache, SupportFragmentInjector {
    override val injector = KodeinInjector()
    override val viewCache: ViewCache = ViewCache { view?.findViewById(it) }

    override fun getLayoutId(): Int {
        return R.layout.intro_fragment_generic
    }

    override fun onAttach(context: Context?) {
        initializeInjector()
        super.onAttach(context)
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyInjector()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        this.viewCache.reset()
    }

    companion object {
        fun newInstance(title: CharSequence, description: CharSequence, imageDrawable: Int, bgColor: Int,
                        titleColor: Int = 0, descColor: Int = 0): GenericAppIntroFragment {

            val slide = GenericAppIntroFragment()
            slide.arguments = Bundle().apply {
                putString(AppIntroBaseFragment.ARG_TITLE, title.toString())
                putString(AppIntroBaseFragment.ARG_DESC, description.toString())
                putInt(AppIntroBaseFragment.ARG_DRAWABLE, imageDrawable)
                putInt(AppIntroBaseFragment.ARG_BG_COLOR, bgColor)
                putInt(AppIntroBaseFragment.ARG_TITLE_COLOR, titleColor)
                putInt(AppIntroBaseFragment.ARG_DESC_COLOR, descColor)
            }

            return slide
        }
    }
}
