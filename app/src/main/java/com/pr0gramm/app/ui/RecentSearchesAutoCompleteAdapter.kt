package com.pr0gramm.app.ui

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter
import com.pr0gramm.app.services.RecentSearchesServices
import java.util.*

/**
 * Auto complete adapter and filter for previous searches.
 */
class RecentSearchesAutoCompleteAdapter(
        suggestionService: RecentSearchesServices,
        context: Context, resource: Int) : ArrayAdapter<String>(context, resource) {

    private val filter = SearchFilter(suggestionService)

    override fun getFilter(): Filter {
        return filter
    }

    private inner class SearchFilter(private val suggestionService: RecentSearchesServices) : Filter() {

        override fun performFiltering(constraint: CharSequence?): Filter.FilterResults {
            if (constraint == null || constraint.isBlank())
                return filterResults(emptyList())

            val term = constraint.toString().lowercase(Locale.getDefault())
            val filtered = suggestionService.searches().filter { term in it.lowercase(Locale.getDefault()) }
            return filterResults(filtered)
        }

        private fun filterResults(names: List<String>): Filter.FilterResults {
            val results = Filter.FilterResults()
            results.values = names
            results.count = names.size
            return results
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: Filter.FilterResults) {
            setNotifyOnChange(false)

            clear()
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
