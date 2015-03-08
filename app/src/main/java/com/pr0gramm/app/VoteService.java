package com.pr0gramm.app;

import android.os.AsyncTask;
import android.util.Log;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Singleton;
import com.orm.SugarRecord;
import com.orm.SugarTransactionHelper;
import com.pr0gramm.app.api.Api;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.Nothing;
import com.pr0gramm.app.feed.Vote;
import com.pr0gramm.app.orm.CachedVote;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import rx.Observable;
import rx.util.async.Async;

import static com.google.common.base.Preconditions.checkArgument;

/**
 */
@Singleton
public class VoteService {
    public static final String TAG = "VoteService";
    private final Api api;

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
        AsyncTask.execute(() -> storeVoteValueInTx(CachedVote.Type.ITEM, item.getId(), vote));
        return api.vote(item.getId(), vote.getVoteValue());
    }

    /**
     * Gets the vote for an item.
     *
     * @param item The item to get the vote for.
     */
    public Observable<Vote> getVote(FeedItem item) {
        // Vote vote = firstNonNull(voteCache.getIfPresent(item.getId()), Vote.NEUTRAL);

        return Async.start(() -> CachedVote.find(CachedVote.Type.ITEM, item.getId()))
                .map(vote -> vote.transform(v -> v.vote))
                .map(vote -> vote.or(Vote.NEUTRAL));
    }

    /**
     * Stores the vote value. This creates a transaction to prevent lost updates.
     *
     * @param type   The type of vote to store in the vote cache.
     * @param itemId The id of the item to vote
     * @param vote   The vote to store for that item
     */
    public void storeVoteValueInTx(CachedVote.Type type, long itemId, Vote vote) {
        SugarTransactionHelper.doInTansaction(() -> {
            storeVoteValue(type, itemId, vote);
        });
    }

    /**
     * Stores a vote value for an item with the given id.
     * This method must be called inside of an transaction to guarantee
     * consistency.
     *
     * @param type   The type of vote to store in the vote cache.
     * @param itemId The id of the item to vote
     * @param vote   The vote to store for that item
     */
    public void storeVoteValue(CachedVote.Type type, long itemId, Vote vote) {
        // check for a previous item
        CachedVote cachedVote = CachedVote.find(type, itemId).orNull();
        if (cachedVote == null) {
            cachedVote = new CachedVote(type, itemId, vote);
        } else {
            cachedVote.vote = vote;
        }

        // and store an item in the database
        cachedVote.save();
    }

    /**
     * Applies the given voting actions from the log.
     *
     * @param actions The actions from the log to apply.
     */
    public void applyVoteActions(List<Integer> actions) {
        checkArgument(actions.size() % 2 == 0, "not an even number of items");

        Stopwatch watch = Stopwatch.createStarted();
        SugarTransactionHelper.doInTansaction(() -> {
            Log.i(TAG, "Applying " + actions.size() / 2 + " vote actions");

            for (int idx = 0; idx < actions.size(); idx += 2) {
                VoteAction action = VOTE_ACTIONS.get(actions.get(idx + 1));
                if (action == null)
                    continue;

                long id = actions.get(idx);
                storeVoteValue(action.type, id, action.vote);
            }
        });

        Log.i(TAG, "Applying vote actions took " + watch);
    }

    public void clear() {
        Log.i(TAG, "Removing all items from vote cache");
        SugarRecord.deleteAll(CachedVote.class);
    }

    private static class VoteAction {
        CachedVote.Type type;
        Vote vote;

        VoteAction(CachedVote.Type type, Vote vote) {
            this.type = type;
            this.vote = vote;
        }
    }

    private static final Map<Integer, VoteAction> VOTE_ACTIONS = new ImmutableMap.Builder<Integer, VoteAction>()
            .put(1, new VoteAction(CachedVote.Type.ITEM, Vote.DOWN))
            .put(2, new VoteAction(CachedVote.Type.ITEM, Vote.NEUTRAL))
            .put(3, new VoteAction(CachedVote.Type.ITEM, Vote.UP))
            .put(4, new VoteAction(CachedVote.Type.COMMENT, Vote.DOWN))
            .put(5, new VoteAction(CachedVote.Type.COMMENT, Vote.NEUTRAL))
            .put(6, new VoteAction(CachedVote.Type.COMMENT, Vote.UP))
            .put(7, new VoteAction(CachedVote.Type.TAG, Vote.DOWN))
            .put(8, new VoteAction(CachedVote.Type.TAG, Vote.NEUTRAL))
            .put(9, new VoteAction(CachedVote.Type.TAG, Vote.UP))
            .put(10, new VoteAction(CachedVote.Type.ITEM, Vote.FAVORITE))
            .build();
}
