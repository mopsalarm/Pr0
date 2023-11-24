package com.pr0gramm.app.ui

import android.net.Uri
import com.google.code.regexp.Pattern
import com.pr0gramm.app.Instant
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.ui.fragments.CommentRef
import java.util.Locale

object FilterParser {
    fun parse(uri: Uri, notificationTime: Instant? = null): FeedFilterWithStart? {
        val uriPath = uri.encodedPath ?: "/"

        val commentId = extractCommentId(uriPath)

        // get the path without optional comment link
        val path = uriPath.replaceFirst(reCommentSuffix, "")

        for (pattern in patterns) {
            val matcher = pattern.matcher(path)
            if (!matcher.matches()) {
                continue
            }

            val encodedGroups = matcher.namedGroups().firstOrNull()?.toMap() ?: continue
            val values = encodedGroups.mapValues { decodeUrlComponent(it.value) }

            var filter = FeedFilter().withFeedType(FeedType.NEW)

            if (values["type"] == "top") {
                filter = filter.withFeedType(FeedType.PROMOTED)
            }

            if (values["type"] == "stalk") {
                filter = filter.withFeedType(FeedType.STALK)
            }

            val tag = values["tag"]
            val user = values["user"]

            if (!user.isNullOrBlank()) {
                when (val subcategory = values["subcategory"]) {
                    null, "uploads" -> {
                        filter = filter
                            .withFeedType(FeedType.NEW)
                            .basicWithUser(user)
                            .withTagsNoReset(tag)
                    }
                    else -> {
                        val collectionTitle = subcategory.replaceFirstChar { ch ->
                            if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                        }

                        filter = filter
                            .withFeedType(FeedType.NEW)
                            .basicWithCollection(user, subcategory, collectionTitle)
                    }
                }
            }

            if (filter.tags == null && !tag.isNullOrBlank()) {
                filter = filter.withTagsNoReset(tag)
            }

            val itemId = values["id"]?.toLongOrNull()
            return FeedFilterWithStart(filter, itemId, commentId, notificationTime)
        }

        return null
    }

    private fun decodeUrlComponent(value: String): String {
        return Uri.decode(value)
    }

    /**
     * Returns the comment id from the path or null, if no comment id
     * is provided.
     */
    private fun extractCommentId(path: String): Long? {
        val matcher = Pattern.compile(":comment([0-9]+)$").matcher(path)
        return if (matcher.find()) matcher.group(1).toLongOrNull() else null
    }

    private val reCommentSuffix = ":comment[0-9]+$".toRegex()

    private val pFeed = Pattern.compile("^/(?<type>new|top|stalk)$")
    private val pFeedId = Pattern.compile("^/(?<type>new|top|stalk)/(?<id>[0-9]+)$")
    private val pUser = Pattern.compile("^/user/(?<user>[^/]+)/?$")
    private val pUserUploads = Pattern.compile("^/user/(?<user>[^/]+)/(?<subcategory>uploads|[^/]+)/?$")
    private val pUserUploadsId = Pattern.compile("^/user/(?<user>[^/]+)/(?<subcategory>uploads|[^/]+)/(?<id>[0-9]+)$")
    private val pUserUploadsWithTag = Pattern.compile("^/user/(?<user>[^/]+)/(?<subcategory>uploads)/(?<tag>[^/]+)$")
    private val pUserUploadsWithTagId =
        Pattern.compile("^/user/(?<user>[^/]+)/(?<subcategory>uploads|[^/]+)/(?<tag>[^/]+)/(?<id>[0-9]+)$")
    private val pTag = Pattern.compile("^/(?<type>new|top)/(?<tag>[^/]+)$")
    private val pTagId = Pattern.compile("^/(?<type>new|top)/(?<tag>[^/]+)/(?<id>[0-9]+)$")

    private val patterns = listOf(
        pFeed,
        pFeedId,
        pUser,
        pUserUploads,
        pUserUploadsId,
        pUserUploadsWithTag,
        pUserUploadsWithTagId,
        pTag,
        pTagId,
    )
}

class FeedFilterWithStart(
    val filter: FeedFilter, start: Long?, commentId: Long?,
    notificationTime: Instant?
) {

    val start: CommentRef? = if (start != null) CommentRef(start, commentId, notificationTime) else null
}
