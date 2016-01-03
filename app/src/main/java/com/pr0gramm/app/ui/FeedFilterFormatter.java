package com.pr0gramm.app.ui;

import android.content.Context;

import com.pr0gramm.app.R;
import com.pr0gramm.app.feed.FeedFilter;

import javax.annotation.Nullable;

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
    public static FeedTitle format(@Nullable Context context, FeedFilter filter) {
        // prevent null pointer exceptions
        if (context == null)
            return new FeedTitle("", "", "");

        if (!filter.isBasic()) {
            if (filter.getTags().isPresent()) {
                return new FeedTitle(
                        filter.getTags().get(),
                        " in ",
                        feedTypeToString(context, filter));
            }

            if (filter.getUsername().isPresent()) {
                return new FeedTitle(
                        context.getString(R.string.filter_format_tag_by) + " " + filter.getUsername().get(),
                        " in ",
                        feedTypeToString(context, filter));
            }

            if (filter.getLikes().isPresent()) {
                return new FeedTitle(
                        context.getString(R.string.filter_format_fav_of) + " " + filter.getLikes().get(),
                        " in ",
                        feedTypeToString(context, filter));
            }
        }

        return new FeedTitle(feedTypeToString(context, filter), "", "");
    }

    public static String feedTypeToString(Context context, FeedFilter filter) {
        switch (filter.getFeedType()) {
            case PROMOTED:
                return context.getString(R.string.filter_format_top);

            case NEW:
                return context.getString(R.string.filter_format_new);

            case PREMIUM:
                return context.getString(R.string.filter_format_premium);

            case RANDOM:
                return context.getString(R.string.filter_format_random);

            case CONTROVERSIAL:
                return context.getString(R.string.filter_format_controversial);

            case BESTOF:
                return context.getString(R.string.filter_format_bestof);

            default:
                throw new IllegalArgumentException("Invalid feed type");
        }
    }

    public static final class FeedTitle {
        public final String title;
        public final String subtitle;
        public final String separator;

        public FeedTitle(String title, String separator, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
            this.separator = separator;
        }

        public String singleline() {
            return title + separator + subtitle;
        }
    }
}
