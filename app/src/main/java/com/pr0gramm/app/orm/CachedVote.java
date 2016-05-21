package com.pr0gramm.app.orm;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.orm.SugarRecord;
import com.pr0gramm.app.feed.Vote;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static com.google.common.collect.Lists.transform;

/**
 */
public class CachedVote extends SugarRecord {
    public long itemId;
    public Type type;
    public Vote vote;

    // for sugar orm
    public CachedVote() {
    }

    public CachedVote(Type type, long itemId, Vote vote) {
        setId(voteId(type, itemId));
        this.itemId = itemId;
        this.type = type;
        this.vote = vote;
    }

    public static Optional<CachedVote> find(Type type, long itemId) {
        CachedVote result = findById(CachedVote.class, voteId(type, itemId));
        return Optional.fromNullable(result);
    }

    public static List<CachedVote> find(Type type, List<Long> ids) {
        if (ids.isEmpty())
            return Collections.emptyList();

        String encodedIds = Joiner.on(",").join(transform(ids, id -> voteId(type, id)));
        return find(CachedVote.class, "id in (" + encodedIds + ")");
    }

    private static long voteId(Type type, long itemId) {
        return itemId * 10 + type.ordinal();
    }

    public static void quickSave(Type type, long itemId, Vote vote) {
        String query = String.format(Locale.ROOT,
                "INSERT OR REPLACE INTO CACHED_VOTE (ID, ITEM_ID, TYPE, VOTE) VALUES (%d, %d, \"%s\", \"%s\")",
                voteId(type, itemId), itemId, type.name(), vote.name());

        CachedVote.executeQuery(query);
    }

    public enum Type {
        ITEM, COMMENT, TAG
    }
}
