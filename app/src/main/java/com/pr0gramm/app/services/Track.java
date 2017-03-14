package com.pr0gramm.app.services;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;

import com.google.common.base.Stopwatch;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.pr0gramm.app.ApplicationClass;
import com.pr0gramm.app.feed.Vote;

import java.util.concurrent.TimeUnit;

import static com.pr0gramm.app.util.FluentBundle.newFluentBundle;

/**
 * Tracking using google analytics. Obviously this is anonymous.
 */
@SuppressWarnings("WeakerAccess")
public final class Track {
    private static final String GA_CUSTOM_AUTHORIZED = "&cm1";

    private Track() {
    }

    public static void loginSuccessful() {
        ga().logEvent(FirebaseAnalytics.Event.LOGIN, null);
    }

    public static void loginFailed() {
        ga().logEvent("login_failed", null);
    }

    public static void logout() {
        ga().logEvent("logout", null);
    }

    public static void search(String query) {
        ga().logEvent(FirebaseAnalytics.Event.SEARCH, newFluentBundle()
                .put(FirebaseAnalytics.Param.SEARCH_TERM, query)
                .put("advanced", false)
                .get());
    }

    public static void writeComment() {
        ga().logEvent("write_comment", null);
    }

    public static void writeMessage() {
        ga().logEvent("write_message", null);
    }

    public static void searchImage() {
        ga().logEvent("search_image", null);
    }

    public static void share(String type) {
        ga().logEvent(FirebaseAnalytics.Event.SHARE, newFluentBundle()
                .put(FirebaseAnalytics.Param.CONTENT_TYPE, type)
                .put(FirebaseAnalytics.Param.ITEM_ID, "0")
                .get());
    }

    public static void votePost(Vote vote) {
        ga().logEvent("Vote" + vote.name(), newFluentBundle()
                .put(FirebaseAnalytics.Param.CONTENT_TYPE, "post")
                .get());
    }

    public static void voteTag(Vote vote) {
        ga().logEvent("Vote" + vote.name(), newFluentBundle()
                .put(FirebaseAnalytics.Param.CONTENT_TYPE, "tag")
                .get());
    }

    public static void voteComment(Vote vote) {
        ga().logEvent("Vote" + vote.name(), newFluentBundle()
                .put(FirebaseAnalytics.Param.CONTENT_TYPE, "comment")
                .get());
    }

    public static void upload(long size) {
        long categoryStart = size / (512 * 1024) * 512;

        @SuppressLint("DefaultLocale")
        String sizeCategory = String.format("%d-%d kb", categoryStart, categoryStart + 512);

        ga().logEvent("upload", newFluentBundle()
                .put("size", sizeCategory)
                .get());
    }

    public static void download() {
        ga().logEvent("download", null);
    }

    public static void statistics() {
        ApplicationClass.appComponent().settingsTracker().track();
    }

    public static void notificationShown() {
        ga().logEvent("notification_shown", null);
    }

    public static void notificationClosed(String method) {
        ga().logEvent("notification_closed", newFluentBundle()
                .put("method", method)
                .get());
    }

    public static void preloadCurrentFeed(int size) {
        ga().logEvent("preload", newFluentBundle()
                .put("item_count", size)
                .get());
    }

    public static void inviteSent() {
        ga().logEvent("invite_sent", null);
    }

    public static void commentFaved() {
        ga().logEvent("kfav_created", null);
    }

    public static void listFavedComments() {
        ga().logEvent("kfav_list", null);
    }

    public static void quickPeek() {
        ga().logEvent("quickpeek", null);
    }

    public static void registerLinkClicked() {
        ga().logEvent("begin_register", null);
    }

    public static void advancedSearch(String query) {
        ga().logEvent(FirebaseAnalytics.Event.SEARCH, newFluentBundle()
                .put(FirebaseAnalytics.Param.SEARCH_TERM, query)
                .put("advanced", true)
                .get());

    }

    public static void passwordChanged() {
        ga().logEvent("password_changed", null);
    }

    public static void secretSantaClicked() {
        ga().logEvent("secret_santa_clicked", null);
    }

    public static void screen(Activity activity, String name) {
        ga().setCurrentScreen(activity, name, name);
    }

    static void updateAuthorizedState(boolean authorized) {
        ga().setUserProperty("authorized", String.valueOf(authorized));
    }

    static void updatePremiumState(boolean premium) {
        ga().setUserProperty("premium", String.valueOf(premium));
    }

    public static void trackApiCallSpeed(Stopwatch watch, String methodName, boolean success) {
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, methodName);
        bundle.putLong(FirebaseAnalytics.Param.VALUE, watch.elapsed(TimeUnit.MILLISECONDS));
        bundle.putBoolean("success", success);
        ga().logEvent("api", bundle);
    }

    private static FirebaseAnalytics ga() {
        return ApplicationClass.appComponent().tracker();
    }
}
