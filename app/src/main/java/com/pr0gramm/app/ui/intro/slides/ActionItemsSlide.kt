package com.pr0gramm.app.ui.intro.slides

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.ColorRes
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.findOptional

/**
 */
abstract class ActionItemsSlide(name: String) : BaseFragment(name) {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {

        return inflater.inflate(layoutId, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val titleView = view.findOptional<TextView>(R.id.title)
        titleView?.text = introTitle

        val descriptionView = view.findOptional<TextView>(R.id.description)
        descriptionView?.text = introDescription

        val actionItems = introActionItems

        val listView = view.find<ListView>(R.id.recycler_view)
        listView.choiceMode = if (singleChoice) ListView.CHOICE_MODE_SINGLE else ListView.CHOICE_MODE_MULTIPLE
        listView.adapter = ArrayAdapter(requireContext(),
                android.R.layout.simple_list_item_multiple_choice, android.R.id.text1,
                actionItems)

        listView.setOnItemClickListener { _, _, position, _ ->
            val item = actionItems[position]
            if (listView.isItemChecked(position)) {
                item.activate()
            } else {
                item.deactivate()
            }
        }

        for (idx in actionItems.indices) {
            if (actionItems[idx].enabled()) {
                listView.setItemChecked(idx, true)
            }
        }

        introBackgroundResource?.let { res -> view.setBackgroundResource(res) }
    }

    open val singleChoice: Boolean = false

    open val layoutId: Int = R.layout.intro_fragment_items

    @ColorRes
    open val introBackgroundResource: Int? = null

    abstract val introTitle: String

    abstract val introDescription: String

    abstract val introActionItems: List<ActionItem>
}
