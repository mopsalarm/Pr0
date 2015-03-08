package com.pr0gramm.app;

import android.os.AsyncTask;
import android.util.Log;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Singleton;
import com.orm.SugarTransactionHelper;
import com.pr0gramm.app.api.Api;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.Nothing;
import com.pr0gramm.app.feed.Vote;
import com.pr0gramm.app.orm.PostMetaData;

import javax.inject.Inject;

import rx.Observable;
import rx.util.async.Async;

import static com.pr0gramm.app.orm.PostMetaData.findByItemId;

/**
 */
@Singleton
public class VoteService {
    private final Api api;

    private final Cache<Long, Vote> voteCache = CacheBuilder.newBuilder().build();

    @Inject
    public VoteService(Api api) {
        this.api = api;
    }

    /**
     * Votes a post. This sends a request to the server, so you need to be signed in
     * to vote posts.
     *
     * @param item The item that is to be voted
     * @param vote The vote to send to the server
     */
    public Observable<Nothing> vote(FeedItem item, Vote vote) {
        voteCache.put(item.getId(), vote);
        AsyncTask.execute(() -> storeVoteValueInTx(item.getId(), vote));

        return api.vote(item.getId(), vote.getVoteValue());
    }

    /**
     * Gets the vote for an item.
     *
     * @param item The item to get the vote for.
     */
    public Observable<Vote> getVote(FeedItem item) {
        // Vote vote = firstNonNull(voteCache.getIfPresent(item.getId()), Vote.NEUTRAL);

        return Async.start(() -> findByItemId(item.getId()))
                .map(meta -> {
                    Log.i("VoteService", "Meta is " + meta);
                    return meta != null ? meta.vote : Vote.NEUTRAL;
                });
    }

    /**
     * Stores the vote value. This creates a transaction to prevent lost updates.
     *
     * @param itemId The id of the item to vote
     * @param vote   The vote to store for that item
     */
    public void storeVoteValueInTx(long itemId, Vote vote) {
        SugarTransactionHelper.doInTansaction(() -> {
            storeVoteValue(itemId, vote);

        });
    }

    /**
     * Stores a vote value for an item with the given id.
     * This method must be called inside of an transaction to guarantee
     * consistency.
     *
     * @param itemId The id of the item to vote
     * @param vote   The vote to store for that item
     */
    public void storeVoteValue(long itemId, Vote vote) {
        // check for a previous item
        PostMetaData metaData = findByItemId(itemId);
        if (metaData == null)
            metaData = new PostMetaData(itemId);

        // and store an item in the database
        metaData.vote = vote;
        metaData.save();
    }
}
