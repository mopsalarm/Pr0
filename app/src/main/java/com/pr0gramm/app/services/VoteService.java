package com.pr0gramm.app.services;

import android.os.AsyncTask;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Singleton;
import com.orm.SugarRecord;
import com.orm.SugarTransactionHelper;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.api.pr0gramm.response.Post;
import com.pr0gramm.app.api.pr0gramm.response.Tag;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.Nothing;
import com.pr0gramm.app.feed.Vote;
import com.pr0gramm.app.orm.CachedVote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import rx.Observable;
import rx.schedulers.Schedulers;
import rx.util.async.Async;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.transform;

/**
 */
@Singleton
public class VoteService {
    private static final Logger logger = LoggerFactory.getLogger(VoteService.class);

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
        logger.info("Voting feed item {} {}", item.getId(), vote);

        AsyncTask.execute(() -> storeVoteValueInTx(CachedVote.Type.ITEM, item.getId(), vote));
        return api.vote(null, item.getId(), vote.getVoteValue());
    }

    public Observable<Nothing> vote(Post.Comment comment, Vote vote) {
        logger.info("Voting comment {} {}", comment.getId(), vote);

        AsyncTask.execute(() -> storeVoteValueInTx(CachedVote.Type.COMMENT, comment.getId(), vote));
        return api.voteComment(null, comment.getId(), vote.getVoteValue());
    }

    public Observable<Nothing> vote(Tag tag, Vote vote) {
        logger.info("Voting tag {} {}", tag.getId(), vote);

        AsyncTask.execute(() -> storeVoteValueInTx(CachedVote.Type.TAG, tag.getId(), vote));
        return api.voteTag(null, tag.getId(), vote.getVoteValue());
    }

    /**
     * Gets the vote for an item.
     *
     * @param item The item to get the vote for.
     */
    public Observable<Vote> getVote(FeedItem item) {
        return Async.start(() -> CachedVote.find(CachedVote.Type.ITEM, item.getId()), Schedulers.io())
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
            logger.info("Applying {} vote actions", actions.size() / 2);

            for (int idx = 0; idx < actions.size(); idx += 2) {
                VoteAction action = VOTE_ACTIONS.get(actions.get(idx + 1));
                if (action == null)
                    continue;

                long id = actions.get(idx);
                storeVoteValue(action.type, id, action.vote);
            }
        });

        logger.info("Applying vote actions took {}", watch);
    }

    /**
     * Tags the given post. This methods adds the tags to the given post
     * and returns a list of tags.
     */
    public Observable<List<Tag>> tag(FeedItem feedItem, List<String> tags) {
        String tagString = Joiner.on(",").join(transform(tags, tag -> tag.replace(',', ' ')));
        return api.addTags(null, feedItem.getId(), tagString).map(response -> {
            SugarTransactionHelper.doInTansaction(() -> {
                // auto-apply up-vote to newly created tags
                for (long tagId : response.getTagIds())
                    storeVoteValue(CachedVote.Type.TAG, tagId, Vote.UP);
            });

            return response.getTags();
        });
    }

    /**
     * Writes a comment to the given post.
     */
    public Observable<List<Post.Comment>> postComment(long itemId, long parentId, String comment) {
        return api.postComment(null, itemId, parentId, comment)
                .filter(response -> response.getComments().size() >= 1)
                .map(response -> {
                    // store the implicit upvote for the comment.
                    storeVoteValueInTx(CachedVote.Type.COMMENT, response.getCommentId(), Vote.UP);
                    return response.getComments();
                });
    }

    public Observable<List<Post.Comment>> postComment(FeedItem item, long parentId, String comment) {
        return postComment(item.getId(), parentId, comment);
    }

    /**
     * Removes all votes from the vote cache.
     */
    public void clear() {
        logger.info("Removing all items from vote cache");
        SugarRecord.deleteAll(CachedVote.class);
    }

    /**
     * Gets the votes for the given comments
     *
     * @param comments A list of comments to get the votes for.
     * @return A map containing the vote from commentId to vote
     */
    public Observable<Map<Long, Vote>> getCommentVotes(List<Post.Comment> comments) {
        List<Long> ids = transform(comments, Post.Comment::getId);
        return findCachedVotes(CachedVote.Type.COMMENT, ids);
    }

    public Observable<Map<Long, Vote>> getTagVotes(List<Tag> tags) {
        List<Long> ids = transform(tags, tag -> (long) tag.getId());
        return findCachedVotes(CachedVote.Type.TAG, ids);
    }

    private Observable<Map<Long, Vote>> findCachedVotes(CachedVote.Type type, List<Long> ids) {
        if (ids.isEmpty())
            return Observable.empty();

        return Async.start(() -> {
            Stopwatch watch = Stopwatch.createStarted();
            List<CachedVote> cachedVotes = CachedVote.find(type, ids);

            Map<Long, Vote> result = new HashMap<>();
            for (CachedVote cachedVote : cachedVotes)
                result.put(cachedVote.itemId, cachedVote.vote);

            logger.info("Loading votes for {} {}s took {}", ids.size(), type.name().toLowerCase(), watch);
            return result;
        }, Schedulers.io());
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
