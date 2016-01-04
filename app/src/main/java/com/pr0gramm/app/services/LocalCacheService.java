package com.pr0gramm.app.services;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Longs;
import com.pr0gramm.app.api.meta.MetaApi;
import com.pr0gramm.app.api.meta.MetaApi.SizeInfo;
import com.pr0gramm.app.api.pr0gramm.response.Tag;
import com.pr0gramm.app.feed.ContentType;
import com.pr0gramm.app.feed.FeedItem;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This service helps to locally cache deltas to the pr0gramm. Those
 * deltas might arise because of cha0s own caching.
 */
@Singleton
public class LocalCacheService {
    private final Cache<Long, ImmutableList<Tag>> tagsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build();

    private final Cache<Long, SizeInfo> thumbCache = CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .build();

    private final Cache<Long, Bitmap> lowQualityPreviewCache = CacheBuilder.newBuilder()
            .maximumSize(5_000)
            .build();

    private final Cache<String, EnhancedUserInfo> userInfoCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    private final AtomicReference<long[]> repostCache = new AtomicReference<>(new long[0]);

    @Inject
    public LocalCacheService() {
    }

    /**
     * Caches (or enhanced) a list of tags for the given itemId.
     *
     * @param tags_ The list with the tags you know about
     * @return A list containing all previously seen tags for this item.
     */
    public ImmutableList<Tag> enhanceTags(Long itemId, Iterable<Tag> tags_) {
        ImmutableList<Tag> tags = ImmutableList.copyOf(tags_);

        ImmutableList<Tag> result = tagsCache.getIfPresent(itemId);
        if (result != null) {
            if (tags.size() > 0) {
                // combine only, if we have input tags
                HashSet<Tag> merged = new HashSet<>(result);
                merged.removeAll(tags);
                merged.addAll(tags);
                result = ImmutableList.copyOf(merged);
            }
        } else {
            // no cached, use the newly provided value
            result = ImmutableList.copyOf(tags);
        }

        tagsCache.put(itemId, result);
        return result;
    }

    public void cacheSizeInfo(SizeInfo info) {
        thumbCache.put(info.getId(), info);
    }

    public Optional<SizeInfo> getSizeInfo(long itemId) {
        return Optional.fromNullable(thumbCache.getIfPresent(itemId));
    }

    /**
     * Caches the given items as reposts.
     */
    public synchronized void cacheReposts(List<Long> newRepostIds) {
        TreeSet<Long> reposts = new TreeSet<>();
        reposts.addAll(Longs.asList(repostCache.get()));
        reposts.addAll(newRepostIds);

        repostCache.set(Longs.toArray(reposts));
    }

    /**
     * Checks if the given item is a repost or not.
     *
     * @param itemId The item to check
     */
    public boolean isRepost(long itemId) {
        return Arrays.binarySearch(repostCache.get(), itemId) >= 0;
    }

    public boolean isRepost(FeedItem item) {
        return isRepost(item.getId());
    }

    /**
     * Stores the given entry for a few minutes in the cache
     */
    public void cacheUserInfo(Set<ContentType> contentTypes, EnhancedUserInfo info) {
        String name = info.getInfo().getUser().getName().trim().toLowerCase();
        String key = name + ContentType.combine(contentTypes);
        userInfoCache.put(key, info);
    }

    /**
     * Gets a cached instance, if there is one.
     */
    public Optional<EnhancedUserInfo> getUserInfo(Set<ContentType> contentTypes, String name) {
        String key = name.trim().toLowerCase() + ContentType.combine(contentTypes);
        return Optional.fromNullable(userInfoCache.getIfPresent(key));
    }

    /**
     * Store a pixels info. This method parses it into a bitmap object.
     */
    public void cacheLowQualityPreviews(MetaApi.PreviewInfo previewInfo) {
        if (previewInfo.width() > 64 || previewInfo.height() > 64)
            return;

        byte[] bytes = BaseEncoding.base64().decode(previewInfo.pixels().replace("\n", ""));
        int[] pixels = new int[previewInfo.width() * previewInfo.height()];
        for (int idx = 0; idx < pixels.length; idx++) {
            int r5 = (bytes[2 * idx] >> 3);
            int g5 = (((bytes[2 * idx + 1] & 0xE0) >> 5) | ((bytes[2 * idx] & 0x07) << 3));
            int b5 = bytes[2 * idx + 1] & 0x1F;
            int r8 = (r5 * 527 + 23) >> 6;
            int g8 = (g5 * 259 + 33) >> 6;
            int b8 = (b5 * 527 + 23) >> 6;

            pixels[idx] = Color.rgb(r8, g8, b8);
        }

        // generate image
        Bitmap bitmap = Bitmap.createBitmap(pixels,
                previewInfo.width(), previewInfo.height(),
                Bitmap.Config.RGB_565);

        lowQualityPreviewCache.put(previewInfo.id(), bitmap);
    }

    public Bitmap lowQualityPreview(long id) {
        return lowQualityPreviewCache.getIfPresent(id);
    }
}
