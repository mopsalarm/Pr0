package com.pr0gramm.app.orm;

import com.google.common.collect.Iterables;
import com.orm.SugarRecord;
import com.pr0gramm.app.feed.Vote;

import java.util.List;

/**
 */
public class PostMetaData extends SugarRecord<PostMetaData> {
    public long itemId;
    public Vote vote;

    public PostMetaData() {
    }

    public PostMetaData(long itemId) {
        this.itemId = itemId;
    }

    public static PostMetaData findByItemId(long itemId) {
        List<PostMetaData> results = find(PostMetaData.class, "item_id=?", String.valueOf(itemId));
        return Iterables.getFirst(results, null);
    }
}
