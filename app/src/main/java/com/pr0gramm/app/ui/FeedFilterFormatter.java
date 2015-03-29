package com.pr0gramm.app.ui;

import android.content.Context;

import com.pr0gramm.app.R;
import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.feed.FeedType;

public class FeedFilterFormatter {
    private FeedFilterFormatter() {
    }

    /**
     * Simple utility function to format a {@link com.pr0gramm.app.feed.FeedFilter} to some
     * string. The string can not be parsed back or anything interesting.
     *
     * @param context The current context
     * @param filter  The filter that is to be converted into a string
     */
    public static String format(Context context, FeedFilter filter) {
        StringBuilder result = new StringBuilder();

        if(filter.isBasic()) {
            if (filter.getFeedType() == FeedType.PROMOTED)
                result.append(context.getString(R.string.action_feed_type_promoted));

            if (filter.getFeedType() == FeedType.NEW)
                result.append(context.getString(R.string.action_feed_type_new));
        }
        else {
            if (filter.getTags().isPresent())
                result.append(filter.getTags().get());

            if (filter.getUsername().isPresent())
                result.append(context.getString(R.string.filter_format_tag_by)).append(" ").append(filter.getUsername().get());

            if (filter.getLikes().isPresent()) {
                result.append(context.getString(R.string.filter_format_fav_of)).append(" ").append(filter.getLikes().get());

            } else {
                result.append(" in ");

                if (filter.getFeedType() == FeedType.PROMOTED)
                    result.append(context.getString(R.string.filter_format_top));

                if (filter.getFeedType() == FeedType.NEW)
                    result.append(context.getString(R.string.filter_format_new));
            }
        }

        return result.toString().trim();
    }
}
