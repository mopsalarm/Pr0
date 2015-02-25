package com.pr0gramm.app;

import com.google.inject.Singleton;
import com.pr0gramm.app.api.Api;
import com.pr0gramm.app.api.Feed;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import rx.Observable;
import rx.functions.Func1;

/**
 */
@Singleton
public class FeedService {
    private final Api api;

    @Inject
    public FeedService(Api api) {
        this.api = api;
    }

    public Observable<List<FeedItem>> getFeed(FeedType type, Set<ContentType> flags) {
        return getFeedStartingAt(Integer.MAX_VALUE, type, flags);
    }

    public Observable<List<FeedItem>> getFeedStartingAt(int id, FeedType type,
                                                        Set<ContentType> flags) {

        int promoted = type == FeedType.PROMOTED ? 1 : 0;
        return api
                .itemsGet(promoted, id, ContentType.combine(flags))
                .map(new Func1<Feed, List<FeedItem>>() {
                    @Override
                    public List<FeedItem> call(Feed feed) {
                        List<FeedItem> result = new ArrayList<>();
                        for (Feed.Item item : feed.getItems())
                            result.add(new FeedItem(item, false));

                        return result;
                    }
                });
    }

    /**
     * Type of the feed - like "new" or "top".
     */
    public enum FeedType {
        NEW, PROMOTED
    }

    public enum ContentType {
        SFW(1, R.string.type_sfw), NSFW(2, R.string.type_nsfw), NSFL(4, R.string.type_nsfl);

        private final int flag;
        private final int title;

        ContentType(int flag, int desc) {
            this.flag = flag;
            this.title = desc;
        }

        public int getTitle() {
            return title;
        }

        public int getFlag() {
            return flag;
        }

        public static int combine(Iterable<ContentType> flags) {
            int sum = 0;
            for (ContentType flag : flags)
                sum += flag.getFlag();

            return sum;
        }
    }
}
