package com.pr0gramm.app.ui.fragments

import android.app.Dialog
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.databinding.AdminTagsDetailsBinding
import com.pr0gramm.app.databinding.TagsDetailsBinding
import com.pr0gramm.app.services.AdminService
import com.pr0gramm.app.ui.base.ViewBindingDialogFragment
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.ui.views.SingleTypeViewBindingAdapter
import com.pr0gramm.app.util.arguments
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.fragmentArgument
import com.pr0gramm.app.util.removeFromParent

/**
 */
class TagsDetailsDialog : ViewBindingDialogFragment<AdminTagsDetailsBinding>("TagsDetailsDialog", AdminTagsDetailsBinding::inflate) {
    private val adminService: AdminService by instance()

    private val itemId: Long by fragmentArgument(name = KEY_FEED_ITEM_ID)

    private val selected: MutableSet<Long> = mutableSetOf()

    override fun onCreateDialog(contentView: View): Dialog {
        return dialog(requireContext()) {
            contentView(contentView)
            negative(R.string.cancel) { dismiss() }
            positive(R.string.action_delete) { onDeleteClicked() }
            noAutoDismiss()
        }
    }

    override fun onDialogViewCreated() {
        views.recyclerView.layoutManager = LinearLayoutManager(
                requireDialog().context, RecyclerView.VERTICAL, false)

        launchWhenStarted {
            showTagsDetails(adminService.tagsDetails(itemId))
        }
    }

    private fun showTagsDetails(tagDetails: Api.TagDetails) {
        // set the new tags and notify the recycler view to redraw itself.
        updateTagsAdapter(tagDetails.tags.sortedBy { it.confidence })
        views.busyIndicator.removeFromParent()
    }

    private fun onDeleteClicked() {
        if (selected.isEmpty()) {
            dismiss()
            return
        }

        val blockAmountStr = if (views.blockUser.isChecked) views.blockUserDays.text.toString() else ""
        val blockAmount = blockAmountStr.toFloatOrNull()

        launchWhenStarted(busyIndicator = true) {
            adminService.deleteTags(itemId, selected, blockAmount)
            dismiss()
        }
    }

    private fun updateTagsAdapter(tags: List<Api.TagDetails.TagInfo>) {
        views.recyclerView.adapter = SingleTypeViewBindingAdapter(TagsDetailsBinding::inflate, tags) { holder, item ->
            holder.bindings.tagText.text = item.tag
            holder.bindings.tagInfo.text = String.format("%s, +%d, -%d", item.user, item.up, item.down)

            holder.bindings.tagText.setOnCheckedChangeListener(null)
            holder.bindings.tagText.isChecked = selected.contains(item.id)

            // register a listener to check/uncheck this tag.
            holder.bindings.tagText.setOnCheckedChangeListener { buttonView, isChecked ->
                val changed: Boolean = if (isChecked) {
                    selected.add(item.id)
                } else {
                    selected.remove(item.id)
                }

                val pos = holder.bindingAdapterPosition
                if (changed && pos != -1) {
                    views.recyclerView.adapter?.notifyItemChanged(pos)
                }
            }
        }
    }

    companion object {
        private const val KEY_FEED_ITEM_ID = "TagsDetailsDialog__feedItem"

        fun newInstance(itemId: Long) = TagsDetailsDialog().arguments {
            putLong(KEY_FEED_ITEM_ID, itemId)
        }
    }
}
