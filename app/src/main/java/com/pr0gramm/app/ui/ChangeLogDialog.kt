package com.pr0gramm.app.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.SpannableStringBuilder
import android.widget.TextView
import com.google.common.base.Charsets
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.pr0gramm.app.R
import com.pr0gramm.app.services.ThemeHelper.accentColor
import com.pr0gramm.app.ui.Truss.Companion.bold
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.views.SimpleAdapter
import com.pr0gramm.app.ui.views.recyclerViewAdapter
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.getColorCompat
import proguard.annotation.Keep
import proguard.annotation.KeepClassMembers
import java.io.IOException
import java.io.InputStreamReader


/**
 */
class ChangeLogDialog : BaseDialogFragment("ChangeLogDialog") {
    private val recyclerView: RecyclerView by bindView(R.id.changelog)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return dialog(context) {
            layout(R.layout.changelog)
            positive()
        }
    }

    override fun onDialogViewCreated() {
        val changes = loadChangelog(context)
        recyclerView.adapter = changeAdapter(changes)
        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun changeAdapter(changeGroups: List<ChangeGroup>): SimpleAdapter<Any> {
        val items: List<Any> = ArrayList<Any>().apply {
            changeGroups.forEachIndexed { idx, group ->
                val current = idx == 0
                add(Version.of(group.version, current))
                addAll(group.changes)
            }
        }

        return recyclerViewAdapter(items) {
            handle<Version>() with layout(R.layout.changelog_version) { view ->
                val textView = view as TextView

                bind { version ->
                    textView.text = version.formatted
                    textView.alpha = if (version.current) 1f else 0.5f
                    textView.setTextColor(textView.context.getColorCompat(accentColor))
                }
            }

            handle<Change>() with layout(R.layout.changelog_change) { view ->
                val textView = view as TextView

                bind { change ->
                    val text = truss {
                        append(change.type, bold)
                        append(" ")
                        append(change.change)
                    }

                    if (change.change.contains("://")) {
                        // might contains links that we want to display?
                        AndroidUtility.linkify(textView, SpannableStringBuilder.valueOf(text))
                    } else {
                        textView.text = text
                    }
                }
            }
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
