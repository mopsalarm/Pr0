package com.pr0gramm.app.orm;

import com.google.common.base.Optional;
import com.orm.SugarRecord;
import com.pr0gramm.app.feed.Vote;

import java.util.List;

import static com.google.common.collect.Iterables.getFirst;

/**
 */
public class CachedVote extends SugarRecord<CachedVote> {
    public long itemId;
    public Type type;
    public Vote vote;

    public CachedVote() {
    }

    public CachedVote(Type type, long itemId, Vote vote) {
        this.itemId = itemId;
        this.type = type;
        this.vote = vote;
    }

    public static Optional<CachedVote> find(Type type, long itemId) {
        List<CachedVote> results = find(CachedVote.class, "item_id=? and type=?",
                String.valueOf(itemId), String.valueOf(type));

        return Optional.fromNullable(getFirst(results, null));
    }

    public enum Type {
        ITEM, COMMENT, TAG
    }
}
