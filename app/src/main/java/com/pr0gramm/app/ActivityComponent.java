package com.pr0gramm.app;

import com.pr0gramm.app.ui.SettingsActivity;
import com.pr0gramm.app.ui.fragments.FavedCommentFragment;
import com.pr0gramm.app.ui.fragments.FeedFragment;
import com.pr0gramm.app.ui.fragments.MessageInboxFragment;
import com.pr0gramm.app.ui.fragments.PostFragment;
import com.pr0gramm.app.ui.fragments.PostPagerFragment;
import com.pr0gramm.app.ui.fragments.PrivateMessageInboxFragment;

import dagger.Subcomponent;

@ContextSingleton
@Subcomponent(modules = {ActivityModule.class})
public interface ActivityComponent {
    void inject(SettingsActivity settingsActivity);

    void inject(FeedFragment feedFragment);

    void inject(PostFragment postFragment);

    void inject(PostPagerFragment postPagerFragment);

    void inject(MessageInboxFragment fragment);

    void inject(FavedCommentFragment fragment);

    void inject(PrivateMessageInboxFragment fragment);
}
