package com.pr0gramm.app.ui

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter
import com.pr0gramm.app.services.UserSuggestionService
import com.pr0gramm.app.util.matches
import java.util.regex.Pattern

/**
 */
class UsernameAutoCompleteAdapter(
        internal val suggestionService: UserSuggestionService,
        context: Context, resource: Int, val prefix: String = "@") : ArrayAdapter<String>(context, resource) {

    private val filter = UsernameFilter()
    private val validConstraint = Pattern.compile(Pattern.quote(this.prefix) + "[a-zA-Z0-9]{2,}")

    override fun getFilter(): Filter {
        return filter
    }

    private inner class UsernameFilter : Filter() {
        override fun performFiltering(constraint: CharSequence?): Filter.FilterResults {
            if (!constraint.matches(validConstraint))
                return toFilterResults(emptyList())

            val term = constraint.toString().substring(prefix.length)
            return toFilterResults(suggestionService.suggestUsers(term).map { prefix + it })
        }

        private fun toFilterResults(names: List<String>): Filter.FilterResults {
            val results = Filter.FilterResults()
            results.values = names
            results.count = names.size
            return results
        }

        override fun publishResults(constraint: CharSequence?, results: Filter.FilterResults) {
            setNotifyOnChange(false)
            clear()

            @Suppress("UNCHECKED_CAST")
            addAll(results.values as List<String>)

            setNotifyOnChange(true)

            if (results.count > 0) {
                notifyDataSetChanged()
            } else {
                notifyDataSetInvalidated()
            }
        }
    }
}
