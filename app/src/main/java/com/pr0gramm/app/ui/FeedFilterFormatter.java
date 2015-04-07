package com.pr0gramm.app.ui;

import android.content.Context;

import com.pr0gramm.app.R;
import com.pr0gramm.app.feed.FeedFilter;

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

        if (filter.isBasic()) {
            result.append(feedTypeToString(context, filter));

        } else {
            if (filter.getTags().isPresent()) {
                result.append(filter.getTags().get());
                result.append(" in ");
                result.append(feedTypeToString(context, filter));
            }

            if (filter.getUsername().isPresent())
                result.append(context.getString(R.string.filter_format_tag_by)).append(" ").append(filter.getUsername().get());

            if (filter.getLikes().isPresent())
                result.append(context.getString(R.string.filter_format_fav_of)).append(" ").append(filter.getLikes().get());
        }

        return result.toString().trim();
    }

    private static String feedTypeToString(Context context, FeedFilter filter) {
        switch (filter.getFeedType()) {
            case PROMOTED:
                return context.getString(R.string.filter_format_top);

            case NEW:
                return context.getString(R.string.filter_format_new);

            case PREMIUM:
                return context.getString(R.string.filter_format_premium);

            default:
                throw new IllegalArgumentException("Invalid feed type");
        }
    }
}
