package com.pr0gramm.app.ui

import android.app.Dialog
import android.content.Context
import android.text.SpannableStringBuilder
import android.view.View
import androidx.core.text.bold
import androidx.core.text.color
import androidx.recyclerview.widget.LinearLayoutManager
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.R
import com.pr0gramm.app.adapter
import com.pr0gramm.app.databinding.ChangelogBinding
import com.pr0gramm.app.databinding.ChangelogChangeBinding
import com.pr0gramm.app.databinding.ChangelogVersionBinding
import com.pr0gramm.app.model.update.Change
import com.pr0gramm.app.model.update.ChangeGroup
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.base.ViewBindingDialogFragment
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.Linkify
import com.pr0gramm.app.util.getColorCompat
import com.pr0gramm.app.util.setTextFuture
import okio.buffer
import okio.source
import java.io.IOException


/**
 */
class ChangeLogDialog : ViewBindingDialogFragment<ChangelogBinding>("ChangeLogDialog", ChangelogBinding::inflate) {
    override fun onCreateDialog(contentView: View): Dialog {
        return dialog(requireContext()) {
            contentView(contentView)
            positive()
        }
    }

    override fun onDialogViewCreated() {
        val changes = loadChangelog(requireContext())
        views.recyclerView.adapter = changeAdapter(changes)
        views.recyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun changeAdapter(changeGroups: List<ChangeGroup>): DelegateAdapter<Any> {
        val versionAdapter = Adapters.ForViewBindings(ChangelogVersionBinding::inflate) { (views), item: Version ->
            views.root.text = item.formatted
        }

        val changeAdapter = Adapters.ForViewBindings(ChangelogChangeBinding::inflate) { (views), change: Change ->
            val textView = views.root

            val text = SpannableStringBuilder()
                    .bold {
                        val color = when (change.type) {
                            "Neu" -> ThemeHelper.accentColor
                            else -> null
                        }

                        if (color != null) {
                            color(textView.context.getColorCompat(color)) { append(change.type) }
                        } else {
                            append(change.type)
                        }
                    }
                    .append("  ")
                    .append(change.change)

            if ("://" in change.change || "@" in change.change) {
                // might contains links that we want to display?
                Linkify.linkify(textView, SpannableStringBuilder.valueOf(text))
            } else {
                textView.setTextFuture(text)
            }
        }

        val adapter = delegateAdapterOf(
                Adapters.adapt(versionAdapter) { item: Any -> item as? Version },
                Adapters.adapt(changeAdapter) { item: Any -> item as? Change },
        )

        adapter.submitList(
                ArrayList<Any>().apply {
                    changeGroups.forEachIndexed { idx, group ->
                        val current = idx == 0
                        add(Version.of(group.version, current))
                        addAll(group.changes)
                    }
                }
        )

        return adapter
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
