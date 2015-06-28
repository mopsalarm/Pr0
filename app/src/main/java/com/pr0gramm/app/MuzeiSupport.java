package com.pr0gramm.app;

import android.content.Intent;
import android.net.Uri;

import com.google.android.apps.muzei.api.Artwork;
import com.google.android.apps.muzei.api.RemoteMuzeiArtSource;
import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.pr0gramm.app.api.pr0gramm.response.Feed;
import com.pr0gramm.app.feed.ContentType;
import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedService;
import com.pr0gramm.app.feed.FeedType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import roboguice.RoboGuice;
import roboguice.util.RoboContext;
import rx.Observable;

import static com.google.common.base.Objects.equal;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.joda.time.Duration.standardMinutes;

/**
 */
public class MuzeiSupport extends RemoteMuzeiArtSource implements RoboContext {
    private static final Logger logger = LoggerFactory.getLogger(MuzeiSupport.class);
    private static final int ROTATE_TIME_MILLIS = (int) standardMinutes(30).getMillis();

    private HashMap<Key<?>, Object> scopedObjects = new HashMap<>();

    @Inject
    private FeedService feedService;

    public MuzeiSupport() {
        super(MuzeiSupport.class.getSimpleName());
    }

    @Override
    public void onCreate() {
        RoboGuice.getInjector(this).injectMembers(this);
        super.onCreate();

        setUserCommands(BUILTIN_COMMAND_ID_NEXT_ARTWORK);
    }

    @Override
    protected void onTryUpdate(int reason) throws RetryException {
        String currentToken = (getCurrentArtwork() != null) ? getCurrentArtwork().getToken() : null;

        FeedFilter filter = new FeedFilter().withTags("earthporn").withFeedType(FeedType.PROMOTED);
        Observable<Feed> feed = feedService.getFeedItems(filter, singleton(ContentType.SFW),
                Optional.<Long>absent(), Optional.<Long>absent());

        List<Feed.Item> items;
        try {
            logger.info("fetching feed");
            items = feed.map(Feed::getItems).toBlocking().singleOrDefault(emptyList());
        } catch (Exception error) {
            logger.warn("could not get feed", error);
            throw new RemoteMuzeiArtSource.RetryException(error);
        }

        if (items.isEmpty()) {
            logger.info("no images found");
            scheduleUpdate(System.currentTimeMillis() + ROTATE_TIME_MILLIS);
            return;
        }

        Feed.Item item = findRandomItem(items, currentToken);

        // get uris for this item
        Uri uri = Uris.get().media(new FeedItem(item), false);
        Uri postUri = Uris.get().post(FeedType.PROMOTED, item.getId());

        // publish to muzei
        publishArtwork(new Artwork.Builder()
                .imageUri(uri)
                .title(item.getUser())
                .viewIntent(new Intent(Intent.ACTION_VIEW, postUri))
                .token(String.valueOf(item.getId()))
                .build());

        scheduleUpdate(System.currentTimeMillis() + ROTATE_TIME_MILLIS);
    }

    private Feed.Item findRandomItem(List<Feed.Item> items, String currentToken) {
        Random random = new Random();

        while (true) {
            Feed.Item item = items.get(random.nextInt(items.size()));
            String token = String.valueOf(item.getId());
            if (items.size() <= 1 || !equal(token, currentToken)) {
                return item;
            }
        }
    }

    @Override
    public Map<Key<?>, Object> getScopedObjectMap() {
        return scopedObjects;
    }
}
