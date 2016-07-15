package com.pr0gramm.app.ui;

import android.net.Uri;

import com.google.code.regexp.Matcher;
import com.google.code.regexp.Pattern;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Longs;
import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.feed.FeedType;
import com.pr0gramm.app.ui.fragments.ItemWithComment;

import java.util.List;
import java.util.Map;

import static com.google.common.base.MoreObjects.firstNonNull;

/**
 */
public class FeedFilterWithStart {
    private final FeedFilter filter;
    private final ItemWithComment start;

    private FeedFilterWithStart(FeedFilter filter, Long start, Long commentId) {
        this.filter = filter;
        this.start = start != null ? new ItemWithComment(start, commentId) : null;
    }

    public FeedFilter getFilter() {
        return filter;
    }

    public Optional<ItemWithComment> getStart() {
        return Optional.fromNullable(start);
    }


    public static Optional<FeedFilterWithStart> fromUri(Uri uri) {
        List<Pattern> patterns = ImmutableList.of(pFeed, pFeedId, pUser, pUserUploads, pUserUploadsId, pTag, pTagId);

        Long commentId = extractCommentId(uri.getPath());

        // get the path without optional comment link
        String path = uri.getPath().replaceFirst(":.*$", "");

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(path);
            if (!matcher.matches())
                continue;

            Map<String, String> groups = matcher.namedGroups();

            FeedFilter filter = new FeedFilter().withFeedType(FeedType.NEW);

            if ("top".equals(groups.get("type")))
                filter = filter.withFeedType(FeedType.PROMOTED);

            if ("stalk".equals(groups.get("type")))
                filter = filter.withFeedType(FeedType.PREMIUM);

            // filter by user
            String user = groups.get("user");
            if (!Strings.isNullOrEmpty(user)) {
                String subcategory = groups.get("subcategory");
                if ("likes".equals(subcategory)) {
                    filter = filter.withLikes(user);
                } else {
                    filter = filter.withUser(user);
                }
            }

            // filter by tag
            String tag = groups.get("tag");
            if (!Strings.isNullOrEmpty(tag))
                filter = filter.withTags(tag);

            Long itemId = Longs.tryParse(firstNonNull(groups.get("id"), ""));
            return Optional.of(new FeedFilterWithStart(filter, itemId, commentId));
        }

        return Optional.absent();
    }

    /**
     * Returns the comment id from the path or null, if no comment id
     * is provided.
     */
    private static Long extractCommentId(String path) {
        Matcher matcher = Pattern.compile(":comment([0-9]+)$").matcher(path);
        return matcher.find() ? Longs.tryParse(matcher.group(1)) : null;
    }

    private static final Pattern pFeed = Pattern.compile("^/(?<type>new|top|stalk$");
    private static final Pattern pFeedId = Pattern.compile("^/(?<type>new|top|stalk)/(?<id>[0-9]+)$");
    private static final Pattern pUser = Pattern.compile("^/user/(?<user>[^/]+)/?$");
    private static final Pattern pUserUploads = Pattern.compile("^/user/(?<user>[^/]+)/(?<subcategory>uploads|likes)/?$");
    private static final Pattern pUserUploadsId = Pattern.compile("^/user/(?<user>[^/]+)/(?<subcategory>uploads|likes)/(?<id>[0-9]+)$");
    private static final Pattern pTag = Pattern.compile("^/(?<type>new|top)/(?<tag>[^/]+)$");
    private static final Pattern pTagId = Pattern.compile("^/(?<type>new|top)/(?<tag>[^/]+)/(?<id>[0-9]+)$");
}
