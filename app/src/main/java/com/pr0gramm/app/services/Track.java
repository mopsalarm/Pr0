package com.pr0gramm.app.services;

import android.annotation.SuppressLint;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.common.base.Stopwatch;
import com.pr0gramm.app.ApplicationClass;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.feed.FeedType;
import com.pr0gramm.app.feed.Vote;

import java.util.concurrent.TimeUnit;

/**
 * Tracking using google analytics. Obviously this is anonymous.
 */
public final class Track {
    private static final String GA_CUSTOM_AUTHORIZED = "&cm1";

    private Track() {
    }

    public static void loginSuccessful() {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("User")
                .setAction("Login")
                .setLabel("Success")
                .build());
    }

    public static void loginFailed() {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("User")
                .setAction("Login")
                .setLabel("Success")
                .build());
    }

    public static void logout() {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("User")
                .setAction("Logout")
                .build());
    }

    public static void search(String query) {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Feed")
                .setAction("Search")
                .setLabel(query)
                .build());
    }

    public static void writeComment() {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("WriteComment")
                .build());
    }

    public static void writeMessage() {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("WriteMessage")
                .build());
    }

    public static void searchImage() {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("SearchImage")
                .build());
    }

    public static void share(String type) {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("Share")
                .setLabel(type)
                .build());
    }

    public static void votePost(Vote vote) {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("Vote" + vote.name())
                .setLabel("Post")
                .build());
    }

    public static void voteTag(Vote vote) {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("Vote" + vote.name())
                .setLabel("Tag")
                .build());
    }

    public static void voteComment(Vote vote) {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("Vote" + vote.name())
                .setLabel("Comment")
                .build());
    }

    public static void upload(long size) {
        long categoryStart = size / (512 * 1024) * 512;

        @SuppressLint("DefaultLocale")
        String sizeCategory = String.format("%d-%d kb", categoryStart, categoryStart + 512);

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("Upload")
                .setLabel(sizeCategory)
                .build());
    }

    public static void download() {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("Download")
                .build());
    }

    public static void statistics(Settings settings, boolean signedIn) {
//        track(new CustomEvent("Settings")
//                .putCustomAttribute("beta", String.valueOf(settings.useBetaChannel()))
//                .putCustomAttribute("signed in", String.valueOf(signedIn))
//                .putCustomAttribute("gif2webm", String.valueOf(settings.convertGifToWebm()))
//                .putCustomAttribute("notifications", String.valueOf(settings.showNotifications()))
//                .putCustomAttribute("mark images", settings.seenIndicatorStyle().name())
//                .putCustomAttribute("https", String.valueOf(settings.useHttps()))
//                .putCustomAttribute("theme", settings.themeName().toLowerCase())
//                .putCustomAttribute("bestof threshold", String.valueOf(settings.bestOfBenisThreshold()))
//                .putCustomAttribute("quick preview", String.valueOf(settings.enableQuickPeek()))
//                .putCustomAttribute("volume navigation", String.valueOf(settings.volumeNavigation()))
//                .putCustomAttribute("hide tag vote buttons", String.valueOf(settings.hideTagVoteButtons()))
//                .putCustomAttribute("incognito browser", String.valueOf(settings.useIncognitoBrowser())));
    }

    public static void notificationShown() {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Notification")
                .setAction("Shown")
                .build());
    }

    public static void notificationClosed(String method) {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Notification")
                .setAction("Closed")
                .setLabel(method)
                .build());
    }

    public static void requestFeed(FeedType feedType) {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Feed")
                .setAction("Load")
                .setLabel(feedType.name())
                .build());
    }

    public static void preloadCurrentFeed(int size) {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Feed")
                .setAction("Preload")
                .setLabel(String.valueOf(size))
                .build());
    }

    public static void inviteSent() {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("User")
                .setAction("Invited")
                .build());
    }

    public static void commentFaved() {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("KFavCreated")
                .build());
    }

    public static void listFavedComments() {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("KFavViewed")
                .build());
    }

    public static void quickPeek() {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Feed")
                .setAction("QuickPeek")
                .build());
    }

    public static void muted(boolean mute) {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction(mute ? "Muted" : "Unmuted")
                .build());
    }

    public static void registerLinkClicked() {
        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Register")
                .setAction("Clicked")
                .build());
    }

    public static void screen(String name) {
        Tracker tr = ApplicationClass.googleAnalyticsTracker();
        tr.setScreenName(name);
        tr.send(new HitBuilders.ScreenViewBuilder().build());
    }

    public static void updateAuthorizedState(boolean authorized) {
        ApplicationClass.googleAnalyticsTracker().set(
                GA_CUSTOM_AUTHORIZED,
                String.valueOf(authorized ? 1 : 0));
    }

    public static void trackApiCallSpeed(Stopwatch watch, String methodName, boolean success) {
        ApplicationClass.googleAnalyticsTracker().send(
                new HitBuilders.TimingBuilder()
                        .setCategory("Api")
                        .setValue(watch.elapsed(TimeUnit.MILLISECONDS))
                        .setVariable(methodName)
                        .setLabel(success ? "success" : "failure")
                        .build());
    }
}
