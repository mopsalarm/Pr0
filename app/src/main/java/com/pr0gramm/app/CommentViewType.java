package com.pr0gramm.app;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.pr0gramm.app.api.Post;

import java.util.List;

import static android.view.ViewGroup.MarginLayoutParams;

/**
 */
public class CommentViewType implements GenericAdapter.ViewType {
    private final ImmutableMap<Integer, Post.Comment> byId;

    public CommentViewType(List<Post.Comment> comments) {
        this.byId = Maps.uniqueIndex(comments, Post.Comment::getId);
    }

    private int getCommentDepth(Post.Comment comment) {
        int depth = 0;
        while (comment != null) {
            depth++;
            comment = byId.get(comment.getParent());
        }

        return depth;
    }

    @Override
    public long getId(Object object) {
        return ((Post.Comment) object).getId();
    }

    @Override
    public RecyclerView.ViewHolder newViewHolder(ViewGroup parent) {
        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.comment, parent, false);

        return new CommentView(view);
    }

    @Override
    public void bind(RecyclerView.ViewHolder holder, Object object) {
        Post.Comment comment = (Post.Comment) object;

        CommentView view = (CommentView) holder;
        view.setCommentDepth(getCommentDepth(comment));
        view.comment.setText(comment.getContent());
        view.name.setUsername(comment.getName(), comment.getMark());
    }

    public static class CommentView extends RecyclerView.ViewHolder {
        final UsernameView name;
        final TextView comment;
        private final int baseLeftMargin;

        public CommentView(View itemView) {
            super(itemView);

            MarginLayoutParams params = (MarginLayoutParams) itemView.getLayoutParams();
            baseLeftMargin = params.leftMargin;

            // get the subviews
            name = (UsernameView) itemView.findViewById(R.id.username);
            comment = (TextView) itemView.findViewById(R.id.comment);
        }

        public void setCommentDepth(int depth) {
            MarginLayoutParams params = (MarginLayoutParams) itemView.getLayoutParams();
            params.leftMargin = baseLeftMargin * depth;
        }
    }
}
