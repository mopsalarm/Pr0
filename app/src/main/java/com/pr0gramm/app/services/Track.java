package com.pr0gramm.app.services;

import android.annotation.SuppressLint;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.common.base.Stopwatch;
import com.pr0gramm.app.ApplicationClass;
import com.pr0gramm.app.feed.Vote;
import com.pr0gramm.app.services.config.Config;

import java.util.concurrent.TimeUnit;

/**
 * Tracking using google analytics. Obviously this is anonymous.
 */
@SuppressWarnings("WeakerAccess")
public final class Track {
    public static final String GA_CUSTOM_AUTHORIZED = "&cd1";
    public static final String GA_CUSTOM_PREMIUM = "&cd2";
    public static final String GA_CUSTOM_ADS = "&cd3";

    private Track() {
    }

    public static void loginSuccessful() {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("User")
                .setAction("Login")
                .setLabel("Success")
                .build());
    }

    public static void loginFailed() {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("User")
                .setAction("Login")
                .setLabel("Success")
                .build());
    }

    public static void logout() {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("User")
                .setAction("Logout")
                .build());
    }

    public static void search(String query) {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("Feed")
                .setAction("Search")
                .setLabel(query)
                .build());
    }

    public static void writeComment() {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("WriteComment")
                .build());
    }

    public static void writeMessage() {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("WriteMessage")
                .build());
    }

    public static void searchImage() {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("SearchImage")
                .build());
    }

    public static void share(String type) {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("Share")
                .setLabel(type)
                .build());
    }

    public static void votePost(Vote vote) {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("Vote" + vote.name())
                .setLabel("Post")
                .build());
    }

    public static void voteTag(Vote vote) {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("Vote" + vote.name())
                .setLabel("Tag")
                .build());
    }

    public static void voteComment(Vote vote) {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("Vote" + vote.name())
                .setLabel("Comment")
                .build());
    }

    public static void upload(long size) {
        long categoryStart = size / (512 * 1024) * 512;

        @SuppressLint("DefaultLocale")
        String sizeCategory = String.format("%d-%d kb", categoryStart, categoryStart + 512);

        ga().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("Upload")
                .setLabel(sizeCategory)
                .build());
    }

    public static void download() {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("Download")
                .build());
    }

    public static void statistics() {
        ApplicationClass.appComponent().settingsTracker().track();
    }

    public static void notificationShown() {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("Notification")
                .setAction("Shown")
                .build());
    }

    public static void notificationClosed(String method) {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("Notification")
                .setAction("Closed")
                .setLabel(method)
                .build());
    }

    public static void preloadCurrentFeed(int size) {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("Feed")
                .setAction("Preload")
                .setLabel(String.valueOf(size))
                .build());
    }

    public static void inviteSent() {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("User")
                .setAction("Invited")
                .build());
    }

    public static void commentFaved() {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("KFavCreated")
                .build());
    }

    public static void listFavedComments() {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("KFavViewed")
                .build());
    }

    public static void quickPeek() {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("Feed")
                .setAction("QuickPeek")
                .build());
    }

    public static void muted(boolean mute) {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction(mute ? "Muted" : "Unmuted")
                .build());
    }

    public static void registerLinkClicked() {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("Register")
                .setAction("Clicked")
                .build());
    }

    public static void advancedSearch(String query) {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("Feed")
                .setAction("AdvancedSearch")
                .setLabel(query)
                .build());
    }

    public static void passwordChanged() {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("User")
                .setAction("PasswordChanged")
                .build());
    }

    public static void secretSantaClicked() {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("SecretSanta")
                .setAction("Clicked")
                .build());
    }

    public static void adClicked(Config.AdType type) {
        ga().send(new HitBuilders.EventBuilder()
                .setCategory("Ads")
                .setAction("Clicked")
                .setLabel(String.valueOf(type))
                .build());
    }

    public static void screen(String name) {
        Tracker tr = ga();
        tr.setScreenName(name);
        tr.send(new HitBuilders.ScreenViewBuilder().build());
    }

    static void updateUserState(UserService.LoginState loginState) {
        Tracker tracker = ga();
        tracker.set(GA_CUSTOM_AUTHORIZED, String.valueOf(loginState.getAuthorized()));
        tracker.set(GA_CUSTOM_PREMIUM, String.valueOf(loginState.getPremium()));
    }

    public static void updateAdType(Config.AdType adType) {
        ga().set(GA_CUSTOM_ADS, String.valueOf(adType));
    }

    public static void trackApiCallSpeed(Stopwatch watch, String methodName, boolean success) {
        ga().send(
                new HitBuilders.TimingBuilder()
                        .setCategory("Api")
                        .setValue(watch.elapsed(TimeUnit.MILLISECONDS))
                        .setVariable(methodName)
                        .setLabel(success ? "success" : "failure")
                        .setNonInteraction(true)
                        .build());
    }

    private static Tracker ga() {
        return ApplicationClass.appComponent().googleAnalyticsTracker();
    }
}
