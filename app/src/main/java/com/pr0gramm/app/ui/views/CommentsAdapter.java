package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableListMultimap;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.view.ViewGroup.MarginLayoutParams;
import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Collections.emptyList;

/**
 */
public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.CommentView> {
    private final Map<Integer, Post.Comment> byId = new HashMap<>();
    private final Map<Long, Vote> voteCache = new HashMap<>();
    private Optional<String> op;

    private List<Post.Comment> comments = new ArrayList<>();
    private CommentActionListener commentActionListener;

    public CommentsAdapter() {
        setHasStableIds(true);
    }

    public void setComments(Collection<Post.Comment> comments, Map<Long, Vote> votes) {
        this.comments = sort(comments);

        this.byId.clear();
        this.byId.putAll(Maps.uniqueIndex(this.comments, c -> (int) c.getId()));

        this.voteCache.clear();
        this.voteCache.putAll(votes);

        notifyDataSetChanged();
    }

    public void clear() {
        comments = emptyList();
        voteCache.clear();
        byId.clear();
        op = Optional.absent();

        notifyDataSetChanged();
    }

    public void setOp(Optional<String> op) {
        this.op = op;
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
        Context context = parent.getContext();
        Settings settings = Settings.of(context);
        int layout = settings.smallerVoteViewsOnComments()
                ? R.layout.comment_smaller_vote_buttons
                : R.layout.comment_normal_vote_buttons;

        View view = LayoutInflater.from(context).inflate(layout, parent, false);
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
        view.senderInfo.setSenderName(comment.getName(), comment.getMark());
        view.senderInfo.setOnSenderClickedListener(v -> doOnAuthorClicked(comment));

        // set the comment and add links
        view.comment.setText(comment.getContent());
        Linkify.addLinks(view.comment, Linkify.WEB_URLS);

        // show the points
        int points = comment.getUp() - comment.getDown();
        view.senderInfo.setPoints(points);
        view.senderInfo.setPointsVisible(true);

        // and the date of the post
        view.senderInfo.setDate(comment.getCreated());

        // enable or disable the badge
        boolean badge = op.transform(op -> op.equalsIgnoreCase(comment.getName())).or(false);
        view.senderInfo.setBadgeOpVisible(badge);

        // and register a vote handler
        view.vote.setVote(firstNonNull(voteCache.get(comment.getId()), Vote.NEUTRAL), true);
        view.vote.setOnVoteListener(vote -> doVote(comment, vote));

        view.senderInfo.setAnswerClickedListener(v -> doAnswer(comment));
    }

    private void doOnAuthorClicked(Post.Comment comment) {
        if (commentActionListener != null)
            commentActionListener.onCommentAuthorClicked(comment);
    }

    private void doAnswer(Post.Comment comment) {
        if (commentActionListener != null)
            commentActionListener.onAnswerClicked(comment);
    }

    private boolean doVote(Post.Comment comment, Vote vote) {
        if (commentActionListener == null)
            return false;

        boolean performVote = commentActionListener.onCommentVoteClicked(comment, vote);
        if (performVote) {
            voteCache.put(comment.getId(), vote);
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
