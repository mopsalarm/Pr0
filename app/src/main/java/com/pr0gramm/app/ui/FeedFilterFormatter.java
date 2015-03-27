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

        if (filter.getFeedType() == FeedType.PROMOTED)
            result.append(context.getString(R.string.action_feed_type_promoted));

        if (filter.getFeedType() == FeedType.NEW)
            result.append(context.getString(R.string.action_feed_type_new));

        result.append(" ");

        if (filter.getTags().isPresent())
            result.append("tags:").append(filter.getTags().get());

        if (filter.getUsername().isPresent())
            result.append("uploads:").append(filter.getUsername().get());

        if (filter.getLikes().isPresent())
            result.append("likes:").append(filter.getLikes().get());

        return result.toString().trim();
    }
}
