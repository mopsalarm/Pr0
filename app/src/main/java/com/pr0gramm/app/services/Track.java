package com.pr0gramm.app.services;

import android.annotation.SuppressLint;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.crashlytics.android.answers.LoginEvent;
import com.crashlytics.android.answers.RatingEvent;
import com.crashlytics.android.answers.SearchEvent;
import com.crashlytics.android.answers.ShareEvent;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.common.base.Stopwatch;
import com.pr0gramm.app.ApplicationClass;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.feed.FeedType;
import com.pr0gramm.app.feed.Vote;

import org.joda.time.DateTimeZone;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import rx.functions.Action1;

/**
 * Tracking using crashlytics answers. Obviously this is anonymous and you can
 * opt-out in the applications settings.
 */
public final class Track {
    private static final Logger logger = LoggerFactory.getLogger("Track");
    private static final String GA_CUSTOM_AUTHORIZED = "&cm1";

    private Track() {
    }

    public static void loginSuccessful() {
        track(answers -> answers.logLogin(new LoginEvent().putSuccess(true)));

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("User")
                .setAction("Login")
                .setLabel("Success")
                .build());
    }

    public static void loginFailed() {
        track(answers -> answers.logLogin(new LoginEvent().putSuccess(false)));

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("User")
                .setAction("Login")
                .setLabel("Success")
                .build());
    }

    public static void logout() {
        track(new CustomEvent("Logout"));

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("User")
                .setAction("Logout")
                .build());
    }

    public static void search(String query) {
        track(answers -> answers.logSearch(new SearchEvent().putQuery(query)));

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Feed")
                .setAction("Search")
                .setLabel(query)
                .build());
    }

    public static void writeComment() {
        track(new CustomEvent("WriteComment"));

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("WriteComment")
                .build());
    }

    public static void writeMessage() {
        track(new CustomEvent("WriteMessage"));

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("WriteMessage")
                .build());
    }

    public static void searchImage() {
        track(new CustomEvent("SearchImage"));

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("SearchImage")
                .build());
    }

    public static void share(String type) {
        track(answers -> answers.logShare(new ShareEvent().putMethod(type)));

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("Share")
                .setLabel(type)
                .build());
    }

    public static void votePost(Vote vote) {
        track(answers -> answers.logRating(new RatingEvent()
                .putContentType("post")
                .putRating(vote.getVoteValue())));

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("Vote" + vote.name())
                .setLabel("Post")
                .build());
    }

    public static void voteTag(Vote vote) {
        track(answers -> answers.logRating(new RatingEvent()
                .putContentType("tag")
                .putRating(vote.getVoteValue())));

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("Vote" + vote.name())
                .setLabel("Tag")
                .build());
    }

    public static void voteComment(Vote vote) {
        track(answers -> answers.logRating(new RatingEvent()
                .putContentType("comment")
                .putRating(vote.getVoteValue())));

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
        track(answers -> answers.logCustom(new CustomEvent("Upload")
                .putCustomAttribute("size", sizeCategory)));

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("Upload")
                .setLabel(sizeCategory)
                .build());
    }

    public static void download() {
        track(new CustomEvent("Download"));

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("Download")
                .build());
    }

    public static void statistics(Settings settings, boolean signedIn) {
        track(new CustomEvent("Settings")
                .putCustomAttribute("beta", String.valueOf(settings.useBetaChannel()))
                .putCustomAttribute("signed in", String.valueOf(signedIn))
                .putCustomAttribute("gif2webm", String.valueOf(settings.convertGifToWebm()))
                .putCustomAttribute("notifications", String.valueOf(settings.showNotifications()))
                .putCustomAttribute("mark images", settings.seenIndicatorStyle().name())
                .putCustomAttribute("https", String.valueOf(settings.useHttps()))
                .putCustomAttribute("theme", settings.themeName().toLowerCase())
                .putCustomAttribute("bestof threshold", String.valueOf(settings.bestOfBenisThreshold()))
                .putCustomAttribute("quick preview", String.valueOf(settings.enableQuickPeek()))
                .putCustomAttribute("volume navigation", String.valueOf(settings.volumeNavigation()))
                .putCustomAttribute("hide tag vote buttons", String.valueOf(settings.hideTagVoteButtons()))
                .putCustomAttribute("incognito browser", String.valueOf(settings.useIncognitoBrowser())));
    }

    public static void bookmarks(int size) {
        track(new CustomEvent("Bookmarks loaded")
                .putCustomAttribute("bookmarks", String.valueOf(size)));
    }

    public static void notificationShown() {
        track(new CustomEvent("Notification shown"));

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Notification")
                .setAction("Shown")
                .build());
    }

    public static void notificationClosed(String method) {
        track(new CustomEvent("Notification closed").putCustomAttribute("method", method));

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Notification")
                .setAction("Closed")
                .setLabel(method)
                .build());
    }

    public static void requestFeed(FeedType feedType) {
        track(new CustomEvent("Load feed")
                .putCustomAttribute("feed type", feedType.name()));

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Feed")
                .setAction("Load")
                .setLabel(feedType.name())
                .build());
    }

    public static void preloadCurrentFeed(int size) {
        int hour = Instant.now().toDateTime(DateTimeZone.UTC).getHourOfDay();

        track(new CustomEvent("Preload current feed")
                .putCustomAttribute("hour", String.valueOf(hour))
                .putCustomAttribute("itemCount", size));

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Feed")
                .setAction("Preload")
                .setLabel(String.valueOf(size))
                .build());
    }

    public static void inviteSent() {
        track(new CustomEvent("Invite sent"));

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("User")
                .setAction("Invited")
                .build());
    }

    public static void commentFaved() {
        track(new CustomEvent("Comment faved"));

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("KFavCreated")
                .build());
    }

    public static void listFavedComments() {
        track(new CustomEvent("Faved comments listed"));

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Content")
                .setAction("KFavViewed")
                .build());
    }

    public static void quickPeek() {
        track(new CustomEvent("Quick peek used"));

        ApplicationClass.googleAnalyticsTracker().send(new HitBuilders.EventBuilder()
                .setCategory("Feed")
                .setAction("QuickPeek")
                .build());
    }

    public static void muted(boolean mute) {
        track(new CustomEvent("Muted").putCustomAttribute("action", mute ? "MUTED" : "UNMUTED"));

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

    private static void track(CustomEvent customEvent) {
        track(answers -> answers.logCustom(customEvent));
    }

    /**
     * Only do the tracking if 'answers' is active.
     */
    private static void track(Action1<Answers> action) {
        try {
            Answers instance = Answers.getInstance();
            if (instance != null) {
                action.call(instance);
            } else {
                logger.info("Would track an event now");
            }
        } catch (IllegalStateException error) {
            logger.warn("Tried to track without initializing crashlytics");
        }
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
