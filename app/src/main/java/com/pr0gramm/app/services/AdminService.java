package com.pr0gramm.app.services;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Longs;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.feed.FeedItem;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import gnu.trove.set.TLongSet;
import rx.Observable;

/**
 */
@Singleton
public class AdminService {
    private final Api api;

    @Inject
    public AdminService(Api api) {
        this.api = api;
    }

    /**
     * Deletes an item. This method will not block the user after deleting the item.
     */
    public Observable<Void> deleteItem(FeedItem item, String reason, boolean notifyUser) {
        return deleteItem(item, reason, notifyUser, null);
    }

    public Observable<Api.TagDetails> tagsDetails(long itemId) {
        return api.tagDetails(itemId);
    }

    public Observable<Void> deleteItem(FeedItem item, String reason, boolean notifyUser, @Nullable Float blockDays) {
        String pNotifyUser = notifyUser ? "on" : null;
        String blockUser = blockDays != null && blockDays >= 0 ? "on" : null;
        return api
                .deleteItem(null, item.id(), "custom", reason, pNotifyUser, blockUser, blockDays)
                .map(response -> (Void) null);
    }

    public Observable<Void> deleteTags(long itemId, TLongSet tagIds, @Nullable Float blockDays) {
        String pBlockUser = blockDays != null ? "on" : null;
        return api
                .deleteTag(null, itemId, pBlockUser, blockDays, Longs.asList(tagIds.toArray()))
                .map(response -> (Void) null);
    }

    public static final ImmutableList<String> REASONS = ImmutableList.of(
            "Repost",
            "Auf Anfrage",
            "Regel #1 - Bild unzureichend getagged (nsfw/nsfl)",
            "Regel #1 - Falsche/Sinnlose Nutzung des NSFP Filters",
            "Regel #2 - Gore/Porn/Suggestive Bilder mit Minderjährigen",
            "Regel #3 - Tierporn",
            "Regel #4 - Stumpfer Rassismus/Nazi-Nostalgie",
            "Regel #5 - Werbung/Spam",
            "Regel #6 - Infos zu Privatpersonen",
            "Regel #7 - Bildqualität",
            "Regel #8 - Ähnliche Bilder in Reihe",
            "Regel #12 - Warez/Logins zu Pay Sites",
            "Regel #14 - Screamer/Sound-getrolle",
            "Regel #15 - reiner Musikupload",
            "Trollscheiße.");

}
