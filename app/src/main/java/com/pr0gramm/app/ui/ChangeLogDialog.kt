package com.pr0gramm.app.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.widget.TextView
import androidx.core.text.bold
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.R
import com.pr0gramm.app.adapter
import com.pr0gramm.app.model.update.Change
import com.pr0gramm.app.model.update.ChangeGroup
import com.pr0gramm.app.services.ThemeHelper.accentColor
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.base.bindView
import com.pr0gramm.app.ui.views.SimpleAdapter
import com.pr0gramm.app.ui.views.recyclerViewAdapter
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.Linkify
import com.pr0gramm.app.util.getColorCompat
import com.pr0gramm.app.util.setTextFuture
import okio.buffer
import okio.source
import java.io.IOException


/**
 */
class ChangeLogDialog : BaseDialogFragment("ChangeLogDialog") {
    private val recyclerView: RecyclerView by bindView(R.id.changelog)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return dialog(requireContext()) {
            layout(R.layout.changelog)
            positive()
        }
    }

    override suspend fun onDialogViewCreated() {
        val changes = loadChangelog(requireContext())
        recyclerView.adapter = changeAdapter(changes)
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
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
                    val text = SpannableStringBuilder()
                            .bold { append(change.type) }
                            .append(" ")
                            .append(change.change)

                    if (change.change.contains("://")) {
                        // might contains links that we want to display?
                        Linkify.linkify(textView, SpannableStringBuilder.valueOf(text))
                    } else {
                        textView.setTextFuture(text)
                    }
                }
            }
        }
    }

    private class Version(val formatted: String, val current: Boolean) {
        companion object {
            fun of(number: Int, current: Boolean): Version {
                return Version("Version 1.$number", current)
            }
        }
    }

    companion object {
        private fun loadChangelog(context: Context): List<ChangeGroup> {
            try {
                context.resources.openRawResource(R.raw.changelog).use { input ->
                    val source = input.source().buffer()
                    return MoshiInstance.adapter<List<ChangeGroup>>().nonNull().fromJson(source)!!
                }
            } catch (error: IOException) {
                AndroidUtility.logToCrashlytics(error)
                return emptyList()
            }
        }
    }
}
