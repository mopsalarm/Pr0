package com.pr0gramm.app.api.pr0gramm.response;

import com.google.common.primitives.Ints;

/**
 */
public class Tag {
    private int id;
    private float confidence;
    private String tag;

    public int getId() {
        return id;
    }

    public float getConfidence() {
        return confidence;
    }

    public String getTag() {
        return tag;
    }

    @Override
    public int hashCode() {
        return Ints.hashCode(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != Tag.class)
            return false;

        Tag other = (Tag) obj;
        return id == other.id;
    }
}
