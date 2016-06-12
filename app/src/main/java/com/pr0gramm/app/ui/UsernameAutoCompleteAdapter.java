package com.pr0gramm.app.ui;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import com.google.common.collect.FluentIterable;
import com.pr0gramm.app.services.UserSuggestionService;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static com.pr0gramm.app.util.AndroidUtility.checkNotMainThread;

/**
 */
public class UsernameAutoCompleteAdapter extends ArrayAdapter<String> {
    private final Filter filter = new UsernameFilter();
    private final Pattern validConstraint;
    private final UserSuggestionService suggestionService;
    private final String prefix;

    public UsernameAutoCompleteAdapter(UserSuggestionService suggestionService, Context context, int resource) {
        this(suggestionService, context, "@", resource);
    }

    public UsernameAutoCompleteAdapter(UserSuggestionService suggestionService, Context context, String prefix, int resource) {
        super(context, resource);
        this.suggestionService = suggestionService;
        this.prefix = prefix;

        validConstraint = Pattern.compile(Pattern.quote(this.prefix) + "[a-zA-Z0-9]{2,}");
    }

    @Override
    public Filter getFilter() {
        return filter;
    }

    private class UsernameFilter extends Filter {

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            if (constraint == null || !validConstraint.matcher(constraint).matches())
                return filterResults(Collections.<String>emptyList());

            checkNotMainThread();

            String term = constraint.toString().substring(prefix.length());
            List<String> results = suggestionService.suggestUsers(term);
            return filterResults(FluentIterable
                    .from(results)
                    .transform(name -> prefix + name)
                    .toList());
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
