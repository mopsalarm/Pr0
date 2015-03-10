package com.pr0gramm.app.api;

import com.google.common.primitives.Longs;

import java.util.List;

/**
 */
public class NewTagResponse {
    private long[] tagIds;
    private List<Tag> tags;

    public List<Long> getTagIds() {
        return Longs.asList(tagIds);
    }

    public List<Tag> getTags() {
        return tags;
    }
}
