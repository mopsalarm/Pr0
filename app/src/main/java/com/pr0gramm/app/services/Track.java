package com.pr0gramm.app.services;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.crashlytics.android.answers.LoginEvent;
import com.crashlytics.android.answers.RatingEvent;
import com.crashlytics.android.answers.SearchEvent;
import com.crashlytics.android.answers.ShareEvent;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.feed.FeedType;
import com.pr0gramm.app.feed.Vote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.functions.Action1;

/**
 * Tracking using crashlytics answers. Obviously this is anonymous and you can
 * opt-out in the applications settings.
 */
public final class Track {
    private static final Logger logger = LoggerFactory.getLogger("Track");

    private Track() {
    }

    public static void loginSuccessful() {
        track(answers -> answers.logLogin(new LoginEvent().putSuccess(true)));
    }

    public static void loginFailed() {
        track(answers -> answers.logLogin(new LoginEvent().putSuccess(false)));
    }

    public static void logout() {
        track(answers -> answers.logCustom(new CustomEvent("Logout")));
    }

    public static void search(String query) {
        track(answers -> answers.logSearch(new SearchEvent().putQuery(query)));
    }

    public static void writeComment() {
        track(answers -> answers.logCustom(new CustomEvent("WriteComment")));
    }

    public static void writeMessage() {
        track(answers -> answers.logCustom(new CustomEvent("WriteMessage")));
    }

    public static void searchImage() {
        track(answers -> answers.logCustom(new CustomEvent("SearchImage")));
    }

    public static void share(String type) {
        track(answers -> answers.logShare(new ShareEvent().putMethod(type)));
    }

    public static void votePost(Vote vote) {
        track(answers -> answers.logRating(new RatingEvent()
                .putContentType("post")
                .putRating(vote.getVoteValue())));
    }

    public static void voteTag(Vote vote) {
        track(answers -> answers.logRating(new RatingEvent()
                .putContentType("tag")
                .putRating(vote.getVoteValue())));
    }

    public static void voteComment(Vote vote) {
        track(answers -> answers.logRating(new RatingEvent()
                .putContentType("comment")
                .putRating(vote.getVoteValue())));
    }

    public static void upload(long size) {
        long categoryStart = size / (512 * 1024) * 512;
        String sizeCategory = String.format("%d-%d kb", categoryStart, categoryStart + 512);
        track(answers -> answers.logCustom(new CustomEvent("Upload")
                .putCustomAttribute("size", sizeCategory)));
    }

    public static void download() {
        track(answers -> answers.logCustom(new CustomEvent("Download")));
    }

    public static void statistics(Settings settings, boolean signedIn, boolean unlocked) {
        String decoder;
        if (settings.useSoftwareDecoder()) {
            decoder = settings.forceMpegDecoder() ? "mpeg" : "webm";
        } else {
            decoder = "native";
        }

        track(answers -> answers.logCustom(new CustomEvent("Settings")
                .putCustomAttribute("decoder", decoder)
                .putCustomAttribute("beta", String.valueOf(settings.useBetaChannel()))
                .putCustomAttribute("signed in", String.valueOf(signedIn))
                .putCustomAttribute("gif2webm", String.valueOf(settings.convertGifToWebm()))
                .putCustomAttribute("repost hint", String.valueOf(settings.markRepostsInFeed()))
                .putCustomAttribute("notifications", String.valueOf(settings.showNotifications()))
                .putCustomAttribute("mark images", settings.seenIndicatorStyle().name())
                .putCustomAttribute("image size", String.valueOf(settings.maxImageSize()))
                .putCustomAttribute("https", String.valueOf(settings.useHttps()))
                .putCustomAttribute("api proxy", String.valueOf(settings.useApiProxy()))
                .putCustomAttribute("unlocked", String.valueOf(unlocked))));
    }

    public static void drawerOpened() {
        track(answers -> answers.logCustom(new CustomEvent("Drawer opened")));
    }

    public static void bookmarks(int size) {
        track(answers -> answers.logCustom(new CustomEvent("Bookmarks loaded")
                .putCustomAttribute("count", size)
                .putCustomAttribute("bookmarks", String.valueOf(size))));
    }

    public static void notificationShown() {
        track(answers -> answers.logCustom(new CustomEvent("Notification shown")));
    }

    public static void notificationClicked() {
        track(answers -> answers.logCustom(new CustomEvent("Notification clicked")));
    }

    public static void experimentEvent(String experiment, String caseName, String actionName) {
        track(answers -> answers.logCustom(new CustomEvent(experiment)
                .putCustomAttribute(actionName, caseName)));
    }

    public static void requestFeed(FeedType feedType) {
        track(answers -> answers.logCustom(new CustomEvent("Load feed")
                .putCustomAttribute("feed type", feedType.name())));
    }

    public static void preloadCurrentFeed() {
        track(answers -> answers.logCustom(new CustomEvent("Preload current feed")));
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
            logger.warn("Tried to log without initializing crashlytics");
        }
    }
}
