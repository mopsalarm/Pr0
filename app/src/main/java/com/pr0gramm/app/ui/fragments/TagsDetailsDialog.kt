package com.pr0gramm.app.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.AdminService
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.base.bindView
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.ui.views.recyclerViewAdapter
import com.pr0gramm.app.util.arguments
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.fragmentArgument
import com.pr0gramm.app.util.removeFromParent

/**
 */
class TagsDetailsDialog : BaseDialogFragment("TagsDetailsDialog") {
    private val adminService: AdminService by instance()

    private val itemId: Long by fragmentArgument(name = KEY_FEED_ITEM_ID)

    private val tagsView: androidx.recyclerview.widget.RecyclerView by bindView(R.id.tags)
    private val busyView: View by bindView(R.id.busy_indicator)
    private val blockUser: CheckBox by bindView(R.id.block_user)
    private val blockUserDays: TextView by bindView(R.id.block_user_days)

    private val selected: MutableSet<Long> = mutableSetOf()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return dialog(requireContext()) {
            layout(R.layout.admin_tags_details)
            negative(R.string.cancel) { dismiss() }
            positive(R.string.action_delete) { onDeleteClicked() }
            noAutoDismiss()
        }
    }

    override fun onDialogViewCreated() {
        tagsView.layoutManager = LinearLayoutManager(
                requireDialog().context, RecyclerView.VERTICAL, false)

        launchWhenStarted {
            showTagsDetails(adminService.tagsDetails(itemId))
        }
    }

    private fun showTagsDetails(tagDetails: Api.TagDetails) {
        // set the new tags and notify the recycler view to redraw itself.
        updateTagsAdapter(tagDetails.tags.sortedBy { it.confidence })
        busyView.removeFromParent()
    }

    private fun onDeleteClicked() {
        if (selected.isEmpty()) {
            dismiss()
            return
        }

        val blockAmountStr = if (blockUser.isChecked) blockUserDays.text.toString() else ""
        val blockAmount = blockAmountStr.toFloatOrNull()

        launchWhenStarted(busyIndicator = true) {
            adminService.deleteTags(itemId, selected, blockAmount)
            dismiss()
        }
    }

    private fun updateTagsAdapter(tags: List<Api.TagDetails.TagInfo>) {
        tagsView.adapter = recyclerViewAdapter(tags) {
            handle<Api.TagDetails.TagInfo>() with layout(R.layout.tags_details) { holder ->
                val info: TextView = holder.find(R.id.tag_info)
                val checkbox: CheckBox = holder.find(R.id.tag_text)

                bind { item ->
                    checkbox.text = item.tag
                    info.text = String.format("%s, +%d, -%d", item.user, item.up, item.down)

                    checkbox.setOnCheckedChangeListener(null)
                    checkbox.isChecked = selected.contains(item.id)

                    // register a listener to check/uncheck this tag.
                    checkbox.setOnCheckedChangeListener { buttonView, isChecked ->
                        val changed: Boolean = if (isChecked) {
                            selected.add(item.id)
                        } else {
                            selected.remove(item.id)
                        }

                        if (changed && adapterPosition != -1) {
                            tagsView.adapter?.notifyItemChanged(adapterPosition)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val KEY_FEED_ITEM_ID = "TagsDetailsDialog__feedItem"

        fun newInstance(itemId: Long) = TagsDetailsDialog().arguments {
            putLong(KEY_FEED_ITEM_ID, itemId)
        }
    }
}
