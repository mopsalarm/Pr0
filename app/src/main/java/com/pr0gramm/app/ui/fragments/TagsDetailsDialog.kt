package com.pr0gramm.app.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import com.github.salomonbrys.kodein.instance
import com.google.common.primitives.Floats
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.AdminService
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.defaultOnError
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.arguments
import com.pr0gramm.app.util.find
import gnu.trove.set.TLongSet
import gnu.trove.set.hash.TLongHashSet
import kotterknife.bindView
import rx.functions.Action0
import rx.functions.Action1
import java.util.*

/**
 */
class TagsDetailsDialog : BaseDialogFragment() {
    private val adminService: AdminService by instance()

    private val itemId: Long by lazy { arguments.getLong(KEY_FEED_ITEM_ID) }

    private val tagsView: RecyclerView by bindView(R.id.tags)
    private val busyView: View by bindView(R.id.busy_indicator)
    private val blockUser: CheckBox by bindView(R.id.block_user)
    private val blockUserDays: TextView by bindView(R.id.block_user_days)

    private val tags: ArrayList<Api.TagDetails.TagInfo> = ArrayList()
    private val selected: TLongSet = TLongHashSet()

    override fun injectComponent(activityComponent: ActivityComponent) {
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return dialog(context) {
            layout(R.layout.admin_tags_details)
            negative(R.string.cancel) { dismiss() }
            positive(R.string.delete) { onDeleteClicked() }
            noAutoDismiss()
        }
    }

    override fun onDialogViewCreated() {
        tagsView.adapter = TagsAdapter()

        tagsView.layoutManager = LinearLayoutManager(
                dialog.context, LinearLayoutManager.VERTICAL, false)

        adminService.tagsDetails(itemId)
                .compose(bindToLifecycleAsync())
                .subscribe(Action1 { this.showTagsDetails(it) }, defaultOnError())
    }

    private fun showTagsDetails(tagDetails: Api.TagDetails) {
        // set the new tags and notify the recycler view to redraw itself.
        tags.addAll(tagDetails.tags())
        tags.sortByDescending { it.confidence() }

        tagsView.adapter.notifyDataSetChanged()

        AndroidUtility.removeView(busyView)
    }

    private fun onDeleteClicked() {
        if (selected.isEmpty) {
            dismiss()
            return
        }

        val blockAmount = Floats.tryParse(
                if (blockUser.isChecked) blockUserDays.text.toString() else "")

        adminService.deleteTags(itemId, selected, blockAmount)
                .compose(bindToLifecycleAsync<Any>().forCompletable())
                .withBusyDialog(this)
                .subscribe(Action0 { this.dismiss() }, defaultOnError())
    }

    private inner class TagsAdapter : RecyclerView.Adapter<TagsViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagsViewHolder {
            return TagsViewHolder(LayoutInflater
                    .from(dialog.context)
                    .inflate(R.layout.tags_details, parent, false))
        }

        override fun onBindViewHolder(view: TagsViewHolder, position: Int) {
            val item = tags[position]

            view.checkbox.text = item.tag()
            view.info.text = String.format("%s, +%d, -%d", item.user(), item.up(), item.down())

            view.checkbox.setOnCheckedChangeListener(null)
            view.checkbox.isChecked = selected.contains(item.id())

            // register a listener to check/uncheck this tag.
            view.checkbox.setOnCheckedChangeListener { buttonView, isChecked ->
                var changed = false
                if (isChecked) {
                    changed = selected.add(item.id())
                } else {
                    changed = selected.remove(item.id())
                }

                if (changed && view.adapterPosition != -1) {
                    notifyItemChanged(view.adapterPosition)
                }
            }
        }

        override fun getItemCount(): Int {
            return tags.size
        }
    }

    private inner class TagsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val info: TextView = find(R.id.tag_info)
        val checkbox: CheckBox = find(R.id.tag_text)
    }

    companion object {
        private val KEY_FEED_ITEM_ID = "TagsDetailsDialog__feedItem"

        fun newInstance(itemId: Long) = TagsDetailsDialog().arguments {
            putLong(KEY_FEED_ITEM_ID, itemId)
        }
    }
}
