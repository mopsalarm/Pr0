package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.api.pr0gramm.response.Post;
import com.pr0gramm.app.feed.Vote;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

/**
 */
public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.CommentView> {
    private ImmutableList<CommentEntry> comments;
    private Optional<String> op;
    private CommentActionListener commentActionListener;
    private long selectedCommentId;
    private boolean prioritizeOpComments;

    public CommentsAdapter() {
        setHasStableIds(true);
        set(emptyList(), emptyMap());
    }

    public void set(Collection<Post.Comment> comments, Map<Long, Vote> votes, String op) {
        ImmutableMap<Long, Post.Comment> byId = Maps.uniqueIndex(comments, Post.Comment::getId);

        this.op = Optional.fromNullable(op);
        this.comments = FluentIterable.from(sort(comments, op)).transform(comment -> {
            int depth = getCommentDepth(byId, comment);
            Vote baseVote = firstNonNull(votes.get(comment.getId()), Vote.NEUTRAL);
            return new CommentEntry(comment, baseVote, depth);
        }).toList();

        notifyDataSetChanged();
    }

    public void set(Collection<Post.Comment> comments, Map<Long, Vote> votes) {
        set(comments, votes, null);
    }

    public void setPrioritizeOpComments(boolean enabled) {
        prioritizeOpComments = enabled;
        notifyDataSetChanged();
    }

    public void setSelectedCommentId(long id) {
        selectedCommentId = id;
        notifyDataSetChanged();
    }

    @Override
    public CommentView onCreateViewHolder(ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        Settings settings = Settings.of(context);

        View view = LayoutInflater.from(context).inflate(R.layout.comment_vote_buttons, parent, false);
        CommentView commentView = new CommentView(view);
        if (settings.smallerVoteViewsOnComments()) {
            int size = context.getResources().getDimensionPixelSize(
                    R.dimen.smaller_comment_vote_view_size);

            commentView.vote.setSize(size);
        }

        return commentView;
    }

    @Override
    public void onViewRecycled(CommentView holder) {
        super.onViewRecycled(holder);
    }

    @Override
    public void onBindViewHolder(CommentView view, int position) {
        CommentEntry entry = comments.get(position);
        Post.Comment comment = entry.comment;

        view.setCommentDepth(entry.depth);
        view.senderInfo.setSenderName(comment.getName(), comment.getMark());
        view.senderInfo.setOnSenderClickedListener(v -> doOnAuthorClicked(comment));

        // set the comment and add links
        view.comment.setText(comment.getContent());
        Linkify.addLinks(view.comment, Linkify.WEB_URLS);

        // show the points
        view.senderInfo.setPoints(getCommentScore(entry));
        view.senderInfo.setPointsVisible(true);

        // and the date of the post
        view.senderInfo.setDate(comment.getCreated());

        // enable or disable the badge
        boolean badge = op.transform(op -> op.equalsIgnoreCase(comment.getName())).or(false);
        view.senderInfo.setBadgeOpVisible(badge);

        // and register a vote handler
        view.vote.setVote(entry.vote, true);
        view.vote.setOnVoteListener(v -> {
            boolean changed = doVote(entry, v);
            notifyItemChanged(position);
            // view.senderInfo.setPoints(getCommentScore(entry));
            return changed;
        });

        view.senderInfo.setAnswerClickedListener(v -> doAnswer(comment));

        view.itemView.setBackgroundColor(view.itemView.getContext().getResources().getColor(
                comment.getId() == selectedCommentId
                        ? R.color.selected_comment_background
                        : R.color.feed_background));
    }

    private int getCommentScore(CommentEntry entry) {
        int score = entry.comment.getUp() - entry.comment.getDown();
        score += entry.vote.getVoteValue() - entry.baseVote.getVoteValue();
        return score;
    }

    private void doOnAuthorClicked(Post.Comment comment) {
        if (commentActionListener != null)
            commentActionListener.onCommentAuthorClicked(comment);
    }

    private void doAnswer(Post.Comment comment) {
        if (commentActionListener != null)
            commentActionListener.onAnswerClicked(comment);
    }

    private boolean doVote(CommentEntry entry, Vote vote) {
        if (commentActionListener == null)
            return false;

        boolean performVote = commentActionListener.onCommentVoteClicked(entry.comment, vote);
        if (performVote) {
            entry.vote = vote;
        }

        return performVote;
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    @Override
    public long getItemId(int position) {
        return comments.get(position).comment.getId();
    }

    public void setCommentActionListener(CommentActionListener commentActionListener) {
        this.commentActionListener = commentActionListener;
    }

    public static class CommentView extends RecyclerView.ViewHolder {

        final TextView comment;
        final VoteView vote;
        final SenderInfoView senderInfo;

        public CommentView(View itemView) {
            super(itemView);

            // get the subviews
            comment = (TextView) itemView.findViewById(R.id.comment);
            vote = (VoteView) itemView.findViewById(R.id.voting);
            senderInfo = (SenderInfoView) itemView.findViewById(R.id.sender_info);
        }

        public void setCommentDepth(int depth) {
            ((CommentSpacerView) itemView).setDepth(depth);
        }

    }

    public interface CommentActionListener {

        boolean onCommentVoteClicked(Post.Comment comment, Vote vote);

        void onAnswerClicked(Post.Comment comment);

        void onCommentAuthorClicked(Post.Comment comment);

    }

    /**
     * "Flattens" a list of hierarchical comments to a sorted list of comments.
     *
     * @param comments The comments to sort
     */
    private List<Post.Comment> sort(Collection<Post.Comment> comments, String op) {
        ImmutableListMultimap<Long, Post.Comment> byParent =
                Multimaps.index(comments, Post.Comment::getParent);

        ArrayList<Post.Comment> result = new ArrayList<>();
        appendChildComments(result, byParent, 0, op);
        return result;
    }

    private void appendChildComments(List<Post.Comment> target,
                                            ListMultimap<Long, Post.Comment> byParent,
                                            long id, String op) {

        Ordering<Post.Comment> ordering = COMMENT_BY_CONFIDENCE;
        if (op != null && prioritizeOpComments) {
            ordering = Ordering.natural().reverse()
                    .onResultOf((Post.Comment c) -> op.equalsIgnoreCase(c.getName()))
                    .compound(ordering);
        }

        List<Post.Comment> children = ordering.sortedCopy(byParent.get(id));
        for (Post.Comment child : children) {
            target.add(child);
            appendChildComments(target, byParent, (int) child.getId(), op);
        }
    }

    private static int getCommentDepth(Map<Long, Post.Comment> byId, Post.Comment comment) {
        int depth = 0;
        while (comment != null) {
            depth++;
            comment = byId.get(comment.getParent());
        }

        return Math.min(8, depth);
    }

    private static final Ordering<Post.Comment> COMMENT_BY_CONFIDENCE =
            Ordering.natural().reverse().onResultOf(Post.Comment::getConfidence);

    private static class CommentEntry {
        final Post.Comment comment;
        final Vote baseVote;
        final int depth;

        Vote vote;

        public CommentEntry(Post.Comment comment, Vote baseVote, int depth) {
            this.comment = comment;
            this.baseVote = baseVote;
            this.depth = depth;
            this.vote = baseVote;
        }
    }
}
