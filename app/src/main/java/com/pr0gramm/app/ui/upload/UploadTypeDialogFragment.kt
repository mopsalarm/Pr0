package com.pr0gramm.app.ui.upload

import android.os.Bundle
import android.support.design.widget.BottomSheetDialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.MenuSheetView

class UploadTypeDialogFragment : BottomSheetDialogFragment() {
    override fun getTheme(): Int = R.style.MyBottomSheetDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val context = requireContext()

        val menuSheetView = MenuSheetView(context, R.string.hint_upload) { item ->
            dialog?.dismiss()

            if (item.itemId == R.id.action_upload_image) {
                UploadActivity.openForType(context, UploadActivity.MEDIA_TYPE_IMAGE)
            }

            if (item.itemId == R.id.action_upload_video) {
                UploadActivity.openForType(context, UploadActivity.MEDIA_TYPE_VIDEO)
            }
        }

        menuSheetView.inflateMenu(R.menu.menu_upload)

        return menuSheetView
    }
}