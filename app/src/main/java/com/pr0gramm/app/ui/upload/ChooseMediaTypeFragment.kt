package com.pr0gramm.app.ui.upload

import android.os.Bundle
import android.view.View
import com.pr0gramm.app.R
import com.pr0gramm.app.databinding.FragmentChooseMediaTypeBinding
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.base.bindViews

/**
 */
class ChooseMediaTypeFragment : BaseFragment("ChooseMediaTypeFragment", R.layout.fragment_choose_media_type) {
    private val views by bindViews(FragmentChooseMediaTypeBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        views.mediaTypeImage.setOnClickListener { openWithType("image/*") }
        views.mediaTypeVideo.setOnClickListener { openWithType("video/*") }
    }

    private fun openWithType(type: String) {
        val parent = activity
        if (parent is Listener) {
            parent.onMediaTypeChosen(type)
        }
    }

    interface Listener {
        fun onMediaTypeChosen(type: String)
    }
}

