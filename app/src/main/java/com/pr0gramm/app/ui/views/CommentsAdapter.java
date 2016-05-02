package com.pr0gramm.app.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.support.v7.view.ContextThemeWrapper;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import com.pr0gramm.app.api.pr0gramm.response.Comment;
import com.pr0gramm.app.feed.Vote;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.services.Track;
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

import static butterknife.ButterKnife.findById;
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
    private ImmutableList<CommentEntry> allComments, comments;
    private Optional<String> op;
    private CommentActionListener commentActionListener;
    private long selectedCommentId;

    private final Instant scoreVisibleThreshold = now().minus(Hours.ONE.toStandardDuration());
    private TLongSet favedComments = new TLongHashSet();
    private TLongSet collapsedComments = new TLongHashSet();
    private boolean showFavCommentButton;
    private ImmutableMap<Long, Comment> commentsById;
    private ImmutableListMultimap<Long, Comment> commentsByParent;

    public CommentsAdapter(boolean admin, String selfName) {
        this.admin = admin;
        this.selfName = selfName;

        setHasStableIds(true);
        set(emptyList(), emptyMap(), null);
    }

    public void set(Collection<Comment> comments, Map<Long, Vote> votes, String op) {
        this.commentsById = Maps.uniqueIndex(comments, Comment::getId);
        this.commentsByParent = Multimaps.index(comments, Comment::getParent);

        this.op = Optional.fromNullable(op);
        this.allComments = FluentIterable.from(sort(comments, op)).transform(comment -> {
            int depth = getCommentDepth(commentsById, comment);
            Vote baseVote = firstNonNull(votes.get(comment.getId()), Vote.NEUTRAL);
            return new CommentEntry(comment, baseVote, depth);
        }).toList();

        internUpdateComments();
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
        Comment comment = entry.comment;

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

        if (collapsedComments.contains(comment.getId())) {
            view.collapseBadge.setVisibility(View.VISIBLE);
            view.collapseBadge.setText("+" + countChildren(comment));
            view.collapseBadge.setOnClickListener(v -> {
                collapsedComments.remove(comment.getId());
                internUpdateComments();
            });

        } else {
            view.collapseBadge.setVisibility(View.GONE);
        }

        view.actions.setOnClickListener(v -> {
            ContextThemeWrapper context = new ContextThemeWrapper(
                    v.getContext(), R.style.AppTheme_Popup_Orange);

            PopupMenu menu = new PopupMenu(context, v);
            menu.setOnMenuItemClickListener(item -> onActionMenuClicked(comment, item));
            menu.inflate(R.menu.comment_actions);

            if (countChildren(comment) > 0) {
                if (collapsedComments.contains(comment.getId())) {
                    menu.getMenu().findItem(R.id.action_expand).setVisible(true);
                } else {
                    menu.getMenu().findItem(R.id.action_collapse).setVisible(true);
                }
            }

            menu.show();
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

    private boolean onActionMenuClicked(Comment comment, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_collapse:
                collapsedComments.add(comment.getId());
                Track.collapseComments("collapse");
                internUpdateComments();
                return true;

            case R.id.action_expand:
                collapsedComments.remove(comment.getId());
                Track.collapseComments("expand");
                internUpdateComments();
                return true;

            case R.id.action_copy_link:
                if (commentActionListener != null) {
                    commentActionListener.onCopyCommentLink(comment);
                    return true;
                }
        }

        return false;
    }

    private int countChildren(Comment comment) {
        ImmutableList<Comment> children = commentsByParent.get(comment.getId());

        int count = children.size();
        for (Comment child : children)
            count += countChildren(child);

        return count;
    }

    private void internUpdateComments() {
        this.comments = FluentIterable.from(allComments)
                .filter(CommentEntry::isVisible)
                .toList();

        notifyDataSetChanged();
    }

    private CommentScore getCommentScore(CommentEntry entry) {
        int score = entry.comment.getUp() - entry.comment.getDown();
        score += entry.vote.getVoteValue() - entry.baseVote.getVoteValue();
        return new CommentScore(score, entry.comment.getUp(), entry.comment.getDown());
    }

    private void doOnAuthorClicked(Comment comment) {
        if (commentActionListener != null)
            commentActionListener.onCommentAuthorClicked(comment);
    }

    private void doAnswer(Comment comment) {
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
        final View actions;
        final ImageView kFav, reply;
        final TextView collapseBadge;


        public CommentView(View itemView) {
            super(itemView);

            // get the subviews
            comment = findById(itemView, R.id.comment);
            vote = ButterKnife.findById(itemView, R.id.voting);
            senderInfo = ButterKnife.findById(itemView, R.id.sender_info);
            kFav = ButterKnife.findById(itemView, R.id.kfav);
            collapseBadge = ButterKnife.findById(itemView, R.id.collapsed_badge);
            reply = ButterKnife.findById(itemView, R.id.reply);
            actions = ButterKnife.findById(itemView, R.id.actions);
        }

        public void setCommentDepth(int depth) {
            ((CommentSpacerView) itemView).setDepth(depth);
        }
    }

    public interface CommentActionListener {

        boolean onCommentVoteClicked(Comment comment, Vote vote);

        void onAnswerClicked(Comment comment);

        void onCommentAuthorClicked(Comment comment);

        void onCommentMarkAsFavoriteClicked(Comment comment, boolean markAsFavorite);

        void onCopyCommentLink(Comment comment);
    }

    /**
     * "Flattens" a list of hierarchical comments to a sorted list of comments.
     *
     * @param comments The comments to sort
     */
    private List<Comment> sort(Collection<Comment> comments, String op) {
        ImmutableListMultimap<Long, Comment> byParent =
                Multimaps.index(comments, Comment::getParent);

        ArrayList<Comment> result = new ArrayList<>();
        appendChildComments(result, byParent, 0, op);
        return result;
    }

    private void appendChildComments(List<Comment> target,
                                     ListMultimap<Long, Comment> byParent,
                                     long id, String op) {

        Ordering<Comment> ordering = COMMENT_BY_CONFIDENCE;
        if (op != null) {
            ordering = Ordering.natural().reverse()
                    .onResultOf((Comment c) -> op.equalsIgnoreCase(c.getName()))
                    .compound(ordering);
        }

        List<Comment> children = ordering.sortedCopy(byParent.get(id));
        for (Comment child : children) {
            target.add(child);
            appendChildComments(target, byParent, (int) child.getId(), op);
        }
    }

    private static int getCommentDepth(Map<Long, Comment> byId, Comment comment) {
        int depth = 0;
        while (comment != null) {
            depth++;
            comment = byId.get(comment.getParent());
        }

        return Math.min(8, depth);
    }

    private static final Ordering<Comment> COMMENT_BY_CONFIDENCE =
            Ordering.natural().reverse().onResultOf(Comment::getConfidence);

    private class CommentEntry {
        final Comment comment;
        final Vote baseVote;
        final int depth;

        Vote vote;

        public CommentEntry(Comment comment, Vote baseVote, int depth) {
            this.comment = comment;
            this.baseVote = baseVote;
            this.depth = depth;
            this.vote = baseVote;
        }

        boolean isVisible() {
            Comment comment = this.comment;
            do {
                if (collapsedComments.contains(comment.getParent())) {
                    return false;
                }

                comment = commentsById.get(comment.getParent());
            } while (comment != null);

            return true;
        }
    }
}
