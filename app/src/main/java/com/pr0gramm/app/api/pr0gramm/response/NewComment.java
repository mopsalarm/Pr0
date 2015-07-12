package com.pr0gramm.app.api.pr0gramm.response;

import java.util.List;

/**
 */
public class NewComment {
    private long commentId;
    private List<Comment> comments;

    public long getCommentId() {
        return commentId;
    }

    public List<Comment> getComments() {
        return comments;
    }
}
