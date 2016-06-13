package com.pr0gramm.app.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.feed.Vote;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.util.AndroidUtility;

import org.joda.time.Hours;
import org.joda.time.Instant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import butterknife.ButterKnife;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;

import static com.google.common.base.Ascii.equalsIgnoreCase;
import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.joda.time.Instant.now;

/**
 */
public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.CommentView> {
    private final boolean admin;
    private final String selfName;
    private ImmutableList<CommentEntry> comments;
    private Optional<String> op;
    private CommentActionListener commentActionListener;
    private long selectedCommentId;

    private final Instant scoreVisibleThreshold = now().minus(Hours.ONE.toStandardDuration());
    private TLongSet favedComments = new TLongHashSet();
    private boolean showFavCommentButton;

    public CommentsAdapter(boolean admin, String selfName) {
        this.admin = admin;
        this.selfName = selfName;

        setHasStableIds(true);
        set(emptyList(), emptyMap(), null);
    }

    public void set(Collection<Api.Comment> comments, Map<Long, Vote> votes, String op) {
        this.op = Optional.fromNullable(op);

        Map<Long, Api.Comment> byId = Maps.uniqueIndex(comments, Api.Comment::getId);
        this.comments = ImmutableList.copyOf(Lists.transform(sort(comments, op), comment -> {
            int depth = getCommentDepth(byId, comment);
            Vote baseVote = firstNonNull(votes.get(comment.getId()), Vote.NEUTRAL);
            return new CommentEntry(comment, baseVote, depth);
        }));

        notifyDataSetChanged();
    }

    public void setShowFavCommentButton(boolean showFavCommentButton) {
        this.showFavCommentButton = showFavCommentButton;
    }

    public void setSelectedCommentId(long id) {
        if (selectedCommentId != id) {
            selectedCommentId = id;
            notifyDataSetChanged();
        }
    }

    public void setFavedComments(TLongSet favedComments) {
        boolean hasChanged = !this.favedComments.equals(favedComments);
        if (hasChanged) {
            this.favedComments = favedComments;
            notifyDataSetChanged();
        }
    }

    @Override
    public CommentView onCreateViewHolder(ViewGroup parent, int viewType) {
        return new CommentView(LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.comment_layout, parent, false));
    }

    @Override
    public void onViewRecycled(CommentView holder) {
        super.onViewRecycled(holder);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(CommentView view, int position) {
        CommentEntry entry = comments.get(position);
        Api.Comment comment = entry.comment;

        view.setCommentDepth(entry.depth);
        view.senderInfo.setSenderName(comment.getName(), comment.getMark());
        view.senderInfo.setOnSenderClickedListener(v -> doOnAuthorClicked(comment));

        AndroidUtility.linkify(view.comment, comment.getContent());

        // show the points
        if (admin
                || equalsIgnoreCase(comment.getName(), selfName)
                || comment.getCreated().isBefore(scoreVisibleThreshold)) {

            view.senderInfo.setPoints(getCommentScore(entry));
        } else {
            view.senderInfo.setPointsUnknown();
        }

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
            return changed;
        });

        // set alpha for the sub views. sadly, setting alpha on view.itemView is not working
        view.comment.setAlpha(entry.vote == Vote.DOWN ? 0.5f : 1f);
        view.senderInfo.setAlpha(entry.vote == Vote.DOWN ? 0.5f : 1f);

        view.reply.setOnClickListener(v -> doAnswer(comment));

        view.copyCommentLink.setOnClickListener(v -> {
            if (commentActionListener != null) {
                commentActionListener.onCopyCommentLink(comment);
            }
        });

        Context context = view.itemView.getContext();
        if (comment.getId() == selectedCommentId) {
            int color = ContextCompat.getColor(context, R.color.selected_comment_background);
            view.itemView.setBackgroundColor(color);
        } else {
            AndroidUtility.setViewBackground(view.itemView, null);
        }

        if (view.kFav != null) {
            if (showFavCommentButton) {
                boolean isFavorite = favedComments.contains(comment.getId());

                if (isFavorite) {
                    int color = ContextCompat.getColor(context, ThemeHelper.primaryColor());
                    view.kFav.setColorFilter(color);
                    view.kFav.setImageResource(R.drawable.ic_favorite);
                } else {
                    int color = ContextCompat.getColor(context, R.color.grey_700);
                    view.kFav.setColorFilter(color);
                    view.kFav.setImageResource(R.drawable.ic_favorite_border);
                }

                view.kFav.setVisibility(View.VISIBLE);
                view.kFav.setOnClickListener(v ->
                        commentActionListener.onCommentMarkAsFavoriteClicked(comment, !isFavorite));
            } else {
                view.kFav.setVisibility(View.GONE);
            }
        }
    }

    private CommentScore getCommentScore(CommentEntry entry) {
        int score = entry.comment.getUp() - entry.comment.getDown();
        score += entry.vote.getVoteValue() - entry.baseVote.getVoteValue();
        return new CommentScore(score, entry.comment.getUp(), entry.comment.getDown());
    }

    private void doOnAuthorClicked(Api.Comment comment) {
        if (commentActionListener != null)
            commentActionListener.onCommentAuthorClicked(comment);
    }

    private void doAnswer(Api.Comment comment) {
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
        final ImageView kFav, reply;
        final View copyCommentLink;


        public CommentView(View itemView) {
            super(itemView);

            // get the subviews
            comment = ButterKnife.findById(itemView, R.id.comment);
            vote = ButterKnife.findById(itemView, R.id.voting);
            senderInfo = ButterKnife.findById(itemView, R.id.sender_info);
            kFav = ButterKnife.findById(itemView, R.id.kfav);
            reply = ButterKnife.findById(itemView, R.id.reply);
            copyCommentLink = ButterKnife.findById(itemView, R.id.copy_comment_link);
        }

        public void setCommentDepth(int depth) {
            ((CommentSpacerView) itemView).setDepth(depth);
        }
    }

    public interface CommentActionListener {

        boolean onCommentVoteClicked(Api.Comment comment, Vote vote);

        void onAnswerClicked(Api.Comment comment);

        void onCommentAuthorClicked(Api.Comment comment);

        void onCommentMarkAsFavoriteClicked(Api.Comment comment, boolean markAsFavorite);

        void onCopyCommentLink(Api.Comment comment);
    }

    /**
     * "Flattens" a list of hierarchical comments to a sorted list of comments.
     *
     * @param comments The comments to sort
     */
    private List<Api.Comment> sort(Collection<Api.Comment> comments, String op) {
        ImmutableListMultimap<Long, Api.Comment> byParent =
                Multimaps.index(comments, Api.Comment::getParent);

        ArrayList<Api.Comment> result = new ArrayList<>();
        appendChildComments(result, byParent, 0, op);
        return result;
    }

    private void appendChildComments(List<Api.Comment> target,
                                     ListMultimap<Long, Api.Comment> byParent,
                                     long id, String op) {

        Ordering<Api.Comment> ordering = COMMENT_BY_CONFIDENCE;
        if (op != null) {
            ordering = Ordering.natural().reverse()
                    .onResultOf((Api.Comment c) -> op.equalsIgnoreCase(c.getName()))
                    .compound(ordering);
        }

        List<Api.Comment> children = ordering.sortedCopy(byParent.get(id));
        for (Api.Comment child : children) {
            target.add(child);
            appendChildComments(target, byParent, (int) child.getId(), op);
        }
    }

    private static int getCommentDepth(Map<Long, Api.Comment> byId, Api.Comment comment) {
        int depth = 0;
        while (comment != null) {
            depth++;
            comment = byId.get(comment.getParent());
        }

        return Math.min(8, depth);
    }

    private static final Ordering<Api.Comment> COMMENT_BY_CONFIDENCE =
            Ordering.natural().reverse().onResultOf(Api.Comment::getConfidence);

    private class CommentEntry {
        final Api.Comment comment;
        final Vote baseVote;
        final int depth;

        Vote vote;

        public CommentEntry(Api.Comment comment, Vote baseVote, int depth) {
            this.comment = comment;
            this.baseVote = baseVote;
            this.depth = depth;
            this.vote = baseVote;
        }
    }
}
