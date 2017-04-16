package com.pr0gramm.app.ui.intro.slides

import com.github.salomonbrys.kodein.instance
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.services.BookmarkService

/**
 */
class BookmarksActionItemsSlide : ActionItemsSlide() {
    private val bookmarkService: BookmarkService by instance()

    override fun injectComponent(activityComponent: ActivityComponent) {
    }

    override val introBackgroundResource: Int = R.color.blue_primary

    override val introTitle: String = "Lesezeichen"

    override val introDescription: String = "Wähle aus der Liste Lesezeichen aus, die du direkt " +
            "in der Navigation sehen möchtest. Du kannst auch jederzeit weitere Lesezeichen " +
            "mit eigener Suche anlegen."

    override val introActionItems: List<ActionItem>
        get() {
            val f = FeedFilter().withFeedType(FeedType.PROMOTED)

            return listOf(
                    BookmarkActionItem(bookmarkService, "Kein Ton", f.withTags("? -f:sound")),
                    BookmarkActionItem(bookmarkService, "Nur Bilder", f.withTags("? -webm -gif")),
                    BookmarkActionItem(bookmarkService, "Original Content", f.withTags("original content")),
                    BookmarkActionItem(bookmarkService, "0815 & Süßvieh", f.withTags("? 0815|süßvieh|(ficken halt)|(aber schicks keinem)")),
                    BookmarkActionItem(bookmarkService, "Ton nur mit Untertitel", f.withTags("? (-f:sound | (untertitel & -404))")),
                    BookmarkActionItem(bookmarkService, "Keine Videos", f.withTags("? -webm")),
                    BookmarkActionItem(bookmarkService, "Reposts in Top", f.withTags("? repost & f:top")),
                    BookmarkActionItem(bookmarkService, "Nur Schrott", f.withFeedType(FeedType.NEW).withTags("? s:shit")))
        }
}
