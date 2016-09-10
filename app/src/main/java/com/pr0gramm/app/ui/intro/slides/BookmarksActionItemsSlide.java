package com.pr0gramm.app.ui.intro.slides;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;

import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.feed.FeedType;
import com.pr0gramm.app.services.BookmarkService;

import java.util.List;

import javax.inject.Inject;

/**
 */
public class BookmarksActionItemsSlide extends ActionItemsSlide {
    @Inject
    BookmarkService bookmarkService;

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundResource(R.color.blue_primary);
    }

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }

    @Override
    protected String getIntroTitle() {
        return "Lesezeichen";
    }

    @Override
    protected String getIntroDescription() {
        return "Wähle aus der Liste Lesezeichen aus, die du direkt in der Navigation sehen möchtest." +
                " Du kannst auch jederzeit weitere Lesezeichen mit eigener Suche anlegen.";
    }

    @Override
    protected List<ActionItem> getIntroActionItems() {
        FeedFilter f = new FeedFilter().withFeedType(FeedType.PROMOTED);

        return ImmutableList.of(
                new BookmarkActionItem(bookmarkService, "Kein Ton", f.withTags("? -f:sound")),
                new BookmarkActionItem(bookmarkService, "Nur Bilder", f.withTags("? -webm -gif")),
                new BookmarkActionItem(bookmarkService, "Original Content", f.withTags("original content")),
                new BookmarkActionItem(bookmarkService, "0815 & Süßvieh", f.withTags("? 0815|süßvieh|(ficken halt)|(aber schicks keinem)")),
                new BookmarkActionItem(bookmarkService, "Ton nur mit Untertitel", f.withTags("? (-f:sound | (untertitel & -404))")),
                new BookmarkActionItem(bookmarkService, "Keine Videos", f.withTags("? -webm")),
                new BookmarkActionItem(bookmarkService, "Reposts in Top", f.withTags("? repost & f:top")));
    }
}
