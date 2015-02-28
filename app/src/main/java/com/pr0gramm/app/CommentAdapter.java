package com.pr0gramm.app;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.pr0gramm.app.api.Post;

import java.util.List;

import static android.view.ViewGroup.MarginLayoutParams;

/**
 */
public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentView> {
    private final ImmutableList<Post.Comment> comments;
    private final ImmutableMap<Integer, Post.Comment> byId;

    public CommentAdapter(List<Post.Comment> comments) {
        setHasStableIds(true);

        // TODO we need to "flatten" the tree of comments here and order them by score.
        this.comments = ImmutableList.copyOf(comments);
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
    public CommentView onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.comment, parent, false);

        return new CommentView(view);
    }

    @Override
    public void onBindViewHolder(CommentView holder, int position) {
        Post.Comment comment = comments.get(position);

        holder.setCommentDepth(getCommentDepth(comment));
        holder.comment.setText(comment.getContent());
        holder.name.setUsername(comment.getName(), comment.getMark());
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    @Override
    public long getItemId(int position) {
        return comments.get(position).getId();
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
