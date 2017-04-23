package com.pr0gramm.app.ui.upload

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.base.BaseFragment

/**
 */
class ChooseMediaTypeFragment : BaseFragment("ChooseMediaTypeFragment") {
    private val btnImage: View by bindView(R.id.media_type_image)
    private val btnVideo: View by bindView(R.id.media_type_video)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_choose_media_type, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnImage.setOnClickListener { openWithType("image/*") }
        btnVideo.setOnClickListener { openWithType("video/*") }
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

