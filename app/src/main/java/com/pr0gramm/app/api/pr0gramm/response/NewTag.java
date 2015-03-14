package com.pr0gramm.app.api.pr0gramm.response;

import com.google.common.primitives.Longs;

import java.util.List;

/**
 */
public class NewTag {
    private long[] tagIds;
    private List<Tag> tags;

    public List<Long> getTagIds() {
        return Longs.asList(tagIds);
    }

    public List<Tag> getTags() {
        return tags;
    }
}
