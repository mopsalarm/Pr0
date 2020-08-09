package com.pr0gramm.app.ui.intro

import android.os.Bundle
import com.github.paolorotolo.appintro.AppIntroBaseFragment
import com.pr0gramm.app.R

class GenericAppIntroFragment : AppIntroBaseFragment() {
    override fun getLayoutId(): Int {
        return R.layout.intro_fragment_generic
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
