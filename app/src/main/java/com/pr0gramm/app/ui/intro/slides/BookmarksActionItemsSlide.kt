package com.pr0gramm.app.ui.intro.slides

import com.pr0gramm.app.R
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.services.BookmarkService
import com.pr0gramm.app.util.di.instance

/**
 */
class BookmarksActionItemsSlide : ActionItemsSlide("BookmarksActionItemsSlide") {
    private val bookmarkService: BookmarkService by instance()

    override val introBackgroundResource: Int = R.color.blue_primary

    override val introTitle: String = "Lesezeichen"

    override val introDescription: String = "Wähle aus der Liste Lesezeichen aus, die du direkt " +
            "in der Navigation sehen möchtest. Du kannst auch jederzeit weitere Lesezeichen " +
            "mit eigener Suche anlegen oder vorhandene durch langes Drücken löschen."

    override val introActionItems: List<ActionItem>
        get() {
            val f = FeedFilter().withFeedType(FeedType.PROMOTED)

            return listOf(
                    BookmarkActionItem(bookmarkService, "Kein Ton",
                            f.withTags("! -f:sound")),

                    BookmarkActionItem(bookmarkService, "Text in Top",
                            f.withTags("! 'text'")),

                    BookmarkActionItem(bookmarkService, "Community Textposts",
                            f.withTags("! 'text' & 'richtiges grau'")),

                    BookmarkActionItem(bookmarkService, "Nur Bilder",
                            f.withTags("! -video -gif")),

                    BookmarkActionItem(bookmarkService, "Original Content",
                            f.withTags("! 'original content' | 'oc'")),

                    BookmarkActionItem(bookmarkService, "0815 & Süßvieh",
                            f.withTags("! 0815|süßvieh|'ficken halt'|'aber schicks keinem'")),

                    BookmarkActionItem(bookmarkService, "Ton nur mit Untertitel",
                            f.withTags("! (-f:sound | (untertitel & -404))")),

                    BookmarkActionItem(bookmarkService, "Keine Videos",
                            f.withTags("! -video")),

                    BookmarkActionItem(bookmarkService, "Reposts in Top",
                            f.withTags("! 'repost' & f:top")),

                    BookmarkActionItem(bookmarkService, "Nur Schrott",
                            f.withTags("! s:shit").withFeedType(FeedType.NEW)))
        }
}
