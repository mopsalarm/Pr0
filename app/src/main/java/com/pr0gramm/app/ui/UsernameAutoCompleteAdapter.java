package com.pr0gramm.app.ui;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import com.google.common.collect.FluentIterable;
import com.pr0gramm.app.api.meta.MetaService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 */
public class UsernameAutoCompleteAdapter extends ArrayAdapter<String> {
    private static final Logger logger = LoggerFactory.getLogger("UsernameAutoCompleteAdapter");

    private final Filter filter = new UsernameFilter();
    private final Pattern validConstraint = Pattern.compile("@[a-zA-Z0-9]{3,}");
    private final MetaService metaService;

    public UsernameAutoCompleteAdapter(MetaService metaService, Context context, int resource) {
        super(context, resource);
        this.metaService = metaService;
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

            List<String> results = metaService.suggestUsers(constraint.toString().substring(1));
            return filterResults(FluentIterable
                    .from(results)
                    .transform(name -> "@" + name)
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
