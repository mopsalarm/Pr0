package com.pr0gramm.app.ui;

import android.content.Context;
import android.support.annotation.NonNull;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import com.pr0gramm.app.services.RecentSearchesServices;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Auto complete adapter and filter for previous searches.
 */
public class RecentSearchesAutoCompleteAdapter extends ArrayAdapter<String> {
    private final Filter filter;

    public RecentSearchesAutoCompleteAdapter(RecentSearchesServices suggestionService, Context context, int resource) {
        super(context, resource);
        filter = new SearchFilter(suggestionService);
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return filter;
    }

    private class SearchFilter extends Filter {
        private final RecentSearchesServices suggestionService;

        SearchFilter(RecentSearchesServices suggestionService) {
            this.suggestionService = suggestionService;
        }

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            if (constraint == null || constraint.toString().isEmpty())
                return filterResults(Collections.<String>emptyList());

            String term = constraint.toString().toLowerCase();

            List<String> filtered = new ArrayList<>();
            for (String search : suggestionService.searches()) {
                if (search.toLowerCase().contains(term)) {
                    filtered.add(search);
                }
            }

            return filterResults(filtered);
        }

        private FilterResults filterResults(List<String> names) {
            FilterResults results = new FilterResults();
            results.values = names;
            results.count = names.size();
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            setNotifyOnChange(false);
            clear();

            //noinspection unchecked
            addAll((List<String>) results.values);

            setNotifyOnChange(true);

            if (results.count > 0) {
                notifyDataSetChanged();
            } else {
                notifyDataSetInvalidated();
            }
        }
    }
}
