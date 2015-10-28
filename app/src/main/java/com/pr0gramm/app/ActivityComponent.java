package com.pr0gramm.app;

import com.pr0gramm.app.ui.ChangeLogDialog;
import com.pr0gramm.app.ui.FeedbackActivity;
import com.pr0gramm.app.ui.InboxActivity;
import com.pr0gramm.app.ui.MainActivity;
import com.pr0gramm.app.ui.SettingsActivity;
import com.pr0gramm.app.ui.UploadActivity;
import com.pr0gramm.app.ui.WriteMessageActivity;
import com.pr0gramm.app.ui.ZoomViewActivity;
import com.pr0gramm.app.ui.dialogs.LoginActivity;
import com.pr0gramm.app.ui.dialogs.LogoutDialogFragment;
import com.pr0gramm.app.ui.dialogs.NewTagDialogFragment;
import com.pr0gramm.app.ui.dialogs.SearchUserDialog;
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment;
import com.pr0gramm.app.ui.fragments.DrawerFragment;
import com.pr0gramm.app.ui.fragments.FeedFragment;
import com.pr0gramm.app.ui.fragments.MessageInboxFragment;
import com.pr0gramm.app.ui.fragments.PostFragment;
import com.pr0gramm.app.ui.fragments.PostPagerFragment;
import com.pr0gramm.app.ui.fragments.PrivateMessageInboxFragment;
import com.pr0gramm.app.ui.fragments.WrittenCommentFragment;
import com.pr0gramm.app.ui.views.viewer.DelayedMediaView;
import com.pr0gramm.app.ui.views.viewer.Gif2VideoMediaView;
import com.pr0gramm.app.ui.views.viewer.GifMediaView;
import com.pr0gramm.app.ui.views.viewer.ImageMediaView;
import com.pr0gramm.app.ui.views.viewer.ProxyMediaView;
import com.pr0gramm.app.ui.views.viewer.SoftwareVideoMediaView;
import com.pr0gramm.app.ui.views.viewer.VideoMediaView;

import javax.inject.Singleton;

import dagger.Subcomponent;

@Singleton
@Subcomponent(modules = {ActivityModule.class})
public interface ActivityComponent {
    void inject(FeedbackActivity feedbackActivity);

    void inject(InboxActivity inboxActivity);

    void inject(MainActivity mainActivity);

    void inject(SettingsActivity settingsActivity);

    void inject(UploadActivity uploadActivity);

    void inject(WriteMessageActivity writeMessageActivity);

    void inject(ZoomViewActivity zoomViewActivity);

    void inject(LoginActivity activity);

    void inject(DrawerFragment drawerFragment);

    void inject(FeedFragment feedFragment);

    void inject(PostFragment postFragment);

    void inject(PostPagerFragment postPagerFragment);

    void inject(UploadActivity.UploadFragment uploadFragment);

    void inject(MessageInboxFragment messageInboxFragment);

    void inject(WrittenCommentFragment messageInboxFragment);

    void inject(PrivateMessageInboxFragment privateMessageInboxFragment);

    void inject(DelayedMediaView delayedMediaView);

    void inject(Gif2VideoMediaView gif2VideoMediaView);

    void inject(GifMediaView gifMediaView);

    void inject(VideoMediaView videoMediaView);

    void inject(SoftwareVideoMediaView softwareVideoMediaView);

    void inject(ProxyMediaView proxyMediaView);

    void inject(ImageMediaView imageMediaView);

    void inject(ChangeLogDialog changeLogDialog);

    void inject(LogoutDialogFragment logoutDialogFragment);

    void inject(NewTagDialogFragment newTagDialogFragment);

    void inject(UpdateDialogFragment updateDialogFragment);

    void inject(SearchUserDialog searchUserDialog);
}
