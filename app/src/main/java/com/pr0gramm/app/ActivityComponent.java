package com.pr0gramm.app;

import com.pr0gramm.app.ui.ChangeLogDialog;
import com.pr0gramm.app.ui.ContactActivity;
import com.pr0gramm.app.ui.InboxActivity;
import com.pr0gramm.app.ui.InviteActivity;
import com.pr0gramm.app.ui.LoginActivity;
import com.pr0gramm.app.ui.MainActivity;
import com.pr0gramm.app.ui.PasswordRecoveryActivity;
import com.pr0gramm.app.ui.RequestPasswordRecoveryActivity;
import com.pr0gramm.app.ui.RulesActivity;
import com.pr0gramm.app.ui.SettingsActivity;
import com.pr0gramm.app.ui.WriteMessageActivity;
import com.pr0gramm.app.ui.ZoomViewActivity;
import com.pr0gramm.app.ui.dialogs.LogoutDialogFragment;
import com.pr0gramm.app.ui.dialogs.NewTagDialogFragment;
import com.pr0gramm.app.ui.dialogs.PopupPlayer;
import com.pr0gramm.app.ui.dialogs.SearchUserDialog;
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment;
import com.pr0gramm.app.ui.fragments.DownloadUpdateDialog;
import com.pr0gramm.app.ui.fragments.DrawerFragment;
import com.pr0gramm.app.ui.fragments.FavedCommentFragment;
import com.pr0gramm.app.ui.fragments.FavoritesFragment;
import com.pr0gramm.app.ui.fragments.FeedFragment;
import com.pr0gramm.app.ui.fragments.ItemAdminDialog;
import com.pr0gramm.app.ui.fragments.MessageInboxFragment;
import com.pr0gramm.app.ui.fragments.PostFragment;
import com.pr0gramm.app.ui.fragments.PostPagerFragment;
import com.pr0gramm.app.ui.fragments.PrivateMessageInboxFragment;
import com.pr0gramm.app.ui.fragments.WrittenCommentFragment;
import com.pr0gramm.app.ui.intro.slides.BookmarksActionItemsSlide;
import com.pr0gramm.app.ui.upload.UploadActivity;
import com.pr0gramm.app.ui.upload.UploadFragment;
import com.pr0gramm.app.ui.views.viewer.DelayedMediaView;
import com.pr0gramm.app.ui.views.viewer.Gif2VideoMediaView;
import com.pr0gramm.app.ui.views.viewer.GifMediaView;
import com.pr0gramm.app.ui.views.viewer.ImageMediaView;
import com.pr0gramm.app.ui.views.viewer.ProxyMediaView;
import com.pr0gramm.app.ui.views.viewer.VideoMediaView;

import dagger.Subcomponent;

@ContextSingleton
@Subcomponent(modules = {ActivityModule.class})
public interface ActivityComponent {
    void inject(ContactActivity contactActivity);

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

    void inject(UploadFragment uploadFragment);

    void inject(MessageInboxFragment fragment);

    void inject(WrittenCommentFragment fragment);

    void inject(FavedCommentFragment fragment);

    void inject(PrivateMessageInboxFragment fragment);

    void inject(DelayedMediaView delayedMediaView);

    void inject(Gif2VideoMediaView gif2VideoMediaView);

    void inject(GifMediaView gifMediaView);

    void inject(VideoMediaView videoMediaView);

    void inject(ProxyMediaView proxyMediaView);

    void inject(ImageMediaView imageMediaView);

    void inject(ChangeLogDialog changeLogDialog);

    void inject(LogoutDialogFragment logoutDialogFragment);

    void inject(NewTagDialogFragment newTagDialogFragment);

    void inject(UpdateDialogFragment updateDialogFragment);

    void inject(SearchUserDialog searchUserDialog);

    void inject(FavoritesFragment favoritesFragment);

    void inject(RulesActivity rulesActivity);

    void inject(ItemAdminDialog itemAdminDialog);

    void inject(PopupPlayer popupPlayer);

    void inject(DownloadUpdateDialog dialog);

    void inject(InviteActivity inviteActivity);

    void inject(RequestPasswordRecoveryActivity requestPasswordRecoveryActivity);

    void inject(PasswordRecoveryActivity passwordRecoveryActivity);

    void inject(BookmarksActionItemsSlide bookmarksActionItemsSlide);
}
