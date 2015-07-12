package com.pr0gramm.app.api.pr0gramm.response;

import java.util.List;

import static java.util.Collections.emptyList;

/**
 */
public class Post {
    private List<Tag> tags = emptyList();
    private List<Comment> comments = emptyList();

    public List<Tag> getTags() {
        return tags;
    }

    public List<Comment> getComments() {
        return comments;
    }
}
