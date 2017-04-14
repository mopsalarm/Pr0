package com.pr0gramm.app.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.support.annotation.ColorRes
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.common.base.Charsets
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.R
import com.pr0gramm.app.services.ThemeHelper.accentColor
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.util.AndroidUtility
import kotterknife.bindView
import proguard.annotation.Keep
import proguard.annotation.KeepClassMembers
import java.io.IOException
import java.io.InputStreamReader


/**
 */
class ChangeLogDialog : BaseDialogFragment() {
    private val recyclerView: RecyclerView by bindView(R.id.changelog)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return dialog(context) {
            layout(R.layout.changelog)
            positive()
        }
    }

    override fun onDialogViewCreated() {
        val changes = loadChangelog(context)
        recyclerView.adapter = ChangeAdapter(changes)
        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    override fun injectComponent(activityComponent: ActivityComponent) {
    }

    private class ChangeAdapter(changeGroups: List<ChangeGroup>) : RecyclerView.Adapter<ChangeViewHolder>() {
        private val items: List<Any> = ArrayList<Any>().apply {
            changeGroups.forEachIndexed { idx, group ->
                val current = idx == 0
                add(Version.of(group.version, current))
                addAll(group.changes)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChangeViewHolder {
            val inflater = LayoutInflater.from(parent.context)

            return ChangeViewHolder(when (viewType) {
                VIEW_TYPE_VERSION -> inflater.inflate(R.layout.changelog_version, parent, false)
                VIEW_TYPE_CHANGE -> inflater.inflate(R.layout.changelog_change, parent, false)

                else -> throw IllegalArgumentException("invalid view type: " + viewType)
            })
        }

        override fun onBindViewHolder(holder: ChangeViewHolder, position: Int) {
            val item = items[position]

            if (item is Change) {
                holder.setText(item.type, item.change)
            }

            if (item is Version) {
                val version = item
                holder.setVersion(version.formatted)
                holder.setTextColorId(accentColor(), if (version.current) 1f else 0.5f)
            }
        }

        override fun getItemCount(): Int {
            return items.size
        }

        override fun getItemViewType(position: Int): Int {
            return if (items[position] is Version) VIEW_TYPE_VERSION else VIEW_TYPE_CHANGE
        }

        companion object {

            private val VIEW_TYPE_VERSION = 0
            private val VIEW_TYPE_CHANGE = 1
        }
    }

    private class ChangeViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text: TextView = itemView as TextView

        fun setText(type: String, text: String) {
            val bold = StyleSpan(Typeface.BOLD)

            val builder = SpannableStringBuilder()
            builder.append(type)
            builder.append(' ')
            builder.append(text)

            builder.setSpan(bold, 0, type.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)

            if (text.contains("://")) {
                // might contains links that we want to display?
                AndroidUtility.linkify(this.text, builder)
            } else {
                this.text.text = builder
            }
        }

        fun setVersion(version: String) {
            this.text.text = version
        }

        fun setTextColorId(@ColorRes textColorId: Int, alpha: Float) {
            val color = ContextCompat.getColor(itemView.context, textColorId)
            this.text.setTextColor(color)
            this.text.alpha = alpha
        }
    }

    @Keep
    @KeepClassMembers
    private class Change {
        lateinit var type: String
        lateinit var change: String
    }

    @Keep
    @KeepClassMembers
    private class ChangeGroup {
        var version: Int = 0
        lateinit var changes: List<Change>
    }

    private class Version(val formatted: String, val current: Boolean) {
        companion object {
            fun of(number: Int, current: Boolean): Version {
                return Version("Version 1." + number, current)
            }
        }
    }

    companion object {
        private fun loadChangelog(context: Context): List<ChangeGroup> {
            try {
                context.resources.openRawResource(R.raw.changelog).use { input ->
                    val reader = InputStreamReader(input, Charsets.UTF_8)
                    return Gson().fromJson(reader, LIST_OF_CHANGE_GROUPS.type)
                }
            } catch (error: IOException) {
                AndroidUtility.logToCrashlytics(error)
                return emptyList()
            }
        }

        private val LIST_OF_CHANGE_GROUPS = object : TypeToken<List<ChangeGroup>>() {}
    }
}
