package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.response.Post;
import com.pr0gramm.app.feed.Vote;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.view.ViewGroup.MarginLayoutParams;
import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.singleton;
import static net.danlew.android.joda.DateUtils.getRelativeTimeSpanString;

/**
 */
public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.CommentView> {
    private final Map<Integer, Post.Comment> byId = new HashMap<>();
    private final Map<Long, Vote> voteCache = new HashMap<>();
    private List<Post.Comment> comments = new ArrayList<>();

    private CommentActionListener commentActionListener;

    public CommentsAdapter() {
        setHasStableIds(true);
    }

    public void addComments(Collection<Post.Comment> comments) {
        this.comments = sort(newArrayList(concat(this.comments, comments)));
        this.byId.putAll(Maps.uniqueIndex(this.comments, c -> (int) c.getId()));
        notifyDataSetChanged();
    }

    public void addComment(Post.Comment comment, Vote vote) {
        voteCache.put(comment.getId(), vote);
        addComments(singleton(comment));
    }

    public void addVoteCache(Map<Long, Vote> votes) {
        voteCache.putAll(votes);
        notifyDataSetChanged();
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
    public void onViewRecycled(CommentView holder) {
        super.onViewRecycled(holder);
    }

    @Override
    public void onBindViewHolder(CommentView view, int position) {
        Post.Comment comment = comments.get(position);

        view.setCommentDepth(getCommentDepth(comment));
        view.name.setUsername(comment.getName(), comment.getMark());

        // set the comment and add links
        view.comment.setText(comment.getContent());
        Linkify.addLinks(view.comment, Linkify.WEB_URLS);

        // show the points
        Context context = view.itemView.getContext();
        int points = comment.getUp() - comment.getDown();
        view.points.setText(context.getString(R.string.points, points));

        // and the date of the post
        CharSequence date = getRelativeTimeSpanString(context, comment.getCreated());
        view.date.setText(date);

        // and register a vote handler
        view.vote.setVote(firstNonNull(voteCache.get(comment.getId()), Vote.NEUTRAL), true);
        view.vote.setOnVoteListener(vote -> doVote(position, comment, vote));

        view.answer.setOnClickListener(v -> doAnswer(comment));
    }

    private void doAnswer(Post.Comment comment) {
        if (commentActionListener != null)
            commentActionListener.onAnswerClicked(comment);
    }

    private boolean doVote(int position, Post.Comment comment, Vote vote) {
        if (commentActionListener == null)
            return false;

        boolean performVote = commentActionListener.onCommentVoteClicked(comment, vote);
        if (performVote) {
            voteCache.put(comment.getId(), vote);
            notifyItemChanged(position);
        }

        return performVote;
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    @Override
    public long getItemId(int position) {
        return comments.get(position).getId();
    }

    public void setCommentActionListener(CommentActionListener commentActionListener) {
        this.commentActionListener = commentActionListener;
    }

    public CommentActionListener getCommentActionListener() {
        return commentActionListener;
    }

    public static class CommentView extends RecyclerView.ViewHolder {
        final UsernameView name;
        final TextView comment;
        final TextView points;
        final TextView date;
        final View answer;

        final VoteView vote;
        private final int baseLeftMargin;

        public CommentView(View itemView) {
            super(itemView);

            MarginLayoutParams params = (MarginLayoutParams) itemView.getLayoutParams();
            baseLeftMargin = params.leftMargin;

            // get the subviews
            name = (UsernameView) itemView.findViewById(R.id.username);
            comment = (TextView) itemView.findViewById(R.id.comment);
            points = (TextView) itemView.findViewById(R.id.points);
            date = (TextView) itemView.findViewById(R.id.date);
            vote = (VoteView) itemView.findViewById(R.id.voting);
            answer = itemView.findViewById(R.id.answer);
        }

        public void setCommentDepth(int depth) {
            MarginLayoutParams params = (MarginLayoutParams) itemView.getLayoutParams();
            params.leftMargin = baseLeftMargin * depth;
        }
    }

    public interface CommentActionListener {
        boolean onCommentVoteClicked(Post.Comment comment, Vote vote);

        void onAnswerClicked(Post.Comment comment);
    }

    /**
     * "Flattens" a list of hierarchical comments to a sorted list of comments.
     *
     * @param comments The comments to sort
     */
    private static List<Post.Comment> sort(Collection<Post.Comment> comments) {
        ImmutableListMultimap<Integer, Post.Comment> byParent =
                Multimaps.index(comments, Post.Comment::getParent);

        ArrayList<Post.Comment> result = new ArrayList<>();
        appendChildComments(result, byParent, 0);
        return result;
    }

    private static void appendChildComments(List<Post.Comment> target,
                                            ListMultimap<Integer, Post.Comment> byParent, int id) {

        List<Post.Comment> children = COMMENT_BY_CONFIDENCE.sortedCopy(byParent.get(id));
        for (Post.Comment child : children) {
            target.add(child);
            appendChildComments(target, byParent, (int) child.getId());
        }
    }

    private static final Ordering<Post.Comment> COMMENT_BY_CONFIDENCE =
            Ordering.natural().reverse().onResultOf(Post.Comment::getConfidence);
}
