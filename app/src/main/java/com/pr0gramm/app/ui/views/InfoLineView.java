package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.api.pr0gramm.response.Tag;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.Vote;
import com.pr0gramm.app.ui.ConservativeLinearLayoutManager;
import com.pr0gramm.app.ui.MergeRecyclerAdapter;
import com.pr0gramm.app.ui.SingleViewAdapter;
import com.pr0gramm.app.ui.TagCloudLayoutManager;

import org.joda.time.Duration;
import org.joda.time.Instant;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.ButterKnife;
import rx.Observable;
import rx.functions.Actions;

import static com.pr0gramm.app.Dagger.appComponent;
import static com.pr0gramm.app.util.AndroidUtility.checkMainThread;
import static com.pr0gramm.app.util.AndroidUtility.findView;
import static java.lang.Math.min;
import static net.danlew.android.joda.DateUtils.getRelativeTimeSpanString;

/**
 */
public class InfoLineView extends LinearLayout {
    private final VoteView voteView;
    private final TextView ratingView;
    private final TextView dateView;
    private final UsernameView usernameView;
    private final RecyclerView tagsView;
    private final Pr0grammIconView voteFavoriteView;

    @Nullable
    private final Settings settings;

    private final View ratingUnknownView;
    private final boolean admin;
    private OnDetailClickedListener onDetailClickedListener;
    private VoteView.OnVoteListener onVoteListener;

    private FeedItem feedItem;
    private TagVoteListener tagVoteListener;
    private boolean isSelfPost;
    private Runnable onAddTagClickedListener;
    private TagsAdapter tagsAdapter;

    public InfoLineView(Context context) {
        this(context, null);
    }

    public InfoLineView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InfoLineView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        settings = isInEditMode() ? null : Settings.of(context);
        admin = !isInEditMode() && appComponent(context).userService().userIsAdmin();

        setOrientation(VERTICAL);

        inflate(context, R.layout.post_info_line, this);

        // get the views from the hierarchy
        ratingView = findView(this, R.id.rating);
        ratingUnknownView = findView(this, R.id.rating_hidden);
        usernameView = findView(this, R.id.username);
        dateView = findView(this, R.id.date);
        voteView = findView(this, R.id.voting);
        voteFavoriteView = findView(this, R.id.action_favorite);

        tagsView = findView(this, R.id.tags);

        int tagGaps = getResources().getDimensionPixelSize(R.dimen.tag_gap_size);
        if (settings != null && settings.tagCloudView()) {
            tagsView.setItemAnimator(null);
            tagsView.setLayoutManager(new TagCloudLayoutManager(tagGaps, tagGaps, 3));
        } else {
            tagsView.setItemAnimator(null);

            // tagsView.setLayoutManager(new TagCloudLayoutManager(tagGaps, tagGaps, 1));
            tagsView.setLayoutManager(new ConservativeLinearLayoutManager(getContext(), HORIZONTAL, false));
        }

        voteView.setOnVoteListener(newVote -> {
            boolean changed = onVoteListener != null && onVoteListener.onVoteClicked(newVote);
            if (changed)
                updateViewState(newVote);

            return changed;
        });

        voteFavoriteView.setOnClickListener(v -> {
            Vote currentVote = voteView.getVote();
            if (currentVote == Vote.FAVORITE) {
                voteView.setVote(Vote.UP);
            } else {
                voteView.setVote(Vote.FAVORITE);
            }
        });

        setTags(Collections.<Tag, Vote>emptyMap());
    }

    /**
     * Displays the given {@link com.pr0gramm.app.feed.FeedItem} along with
     * the given vote.
     *
     * @param item The item to display
     * @param vote The vote that belongs to the given item.
     */
    public void setFeedItem(FeedItem item, boolean isSelfPost, Observable<Vote> vote) {
        this.feedItem = item;
        this.isSelfPost = isSelfPost;

        // update the views!
        usernameView.setUsername(item.user(), item.mark());
        dateView.setText(getRelativeTimeSpanString(getContext(), item.created()));
        updateViewState(Vote.NEUTRAL);

        usernameView.setOnClickListener(v -> {
            if (onDetailClickedListener != null) {
                String username = item.user();
                onDetailClickedListener.onUserClicked(username);
            }
        });

        vote.subscribe(v -> {
            checkMainThread();
            voteView.setVote(v, true);

            updateViewState(v);
        }, Actions.empty());
    }

    /**
     * Updates the rating using the currently set feed item and the given vote.
     *
     * @param vote The vote that is currently selected.
     */
    private void updateViewState(Vote vote) {
        if (feedItem == null)
            return;

        if (isOneHourOld() || isSelfPost || admin) {
            int rating = feedItem.up() - feedItem.down() + min(1, vote.getVoteValue());
            ratingView.setText(String.valueOf(rating));
            ratingView.setOnLongClickListener(v -> {
                Toast.makeText(getContext(),
                        String.format("%d up, %d down", feedItem.up(), feedItem.down()),
                        Toast.LENGTH_SHORT).show();

                return true;
            });

            ratingView.setVisibility(VISIBLE);
            ratingUnknownView.setVisibility(GONE);

        } else {
            ratingUnknownView.setVisibility(VISIBLE);
            ratingView.setVisibility(GONE);
            ratingView.setOnLongClickListener(null);
        }

        voteFavoriteView.setTextColor(vote == Vote.FAVORITE
                ? voteView.getMarkedColor()
                : voteView.getDefaultColor());
    }

    public VoteView getVoteView() {
        return voteView;
    }

    public void setTags(Map<Tag, Vote> tags) {
        List<Tag> sorted = Ordering.natural().reverse()
                .onResultOf(Tag::getConfidence)
                .sortedCopy(tags.keySet());

        View addTagView = LayoutInflater.from(getContext()).inflate(R.layout.tags_add, null);
        addTagView.setOnClickListener(v -> {
            if (onAddTagClickedListener != null) {
                onAddTagClickedListener.run();
            }
        });

        addTagView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        addTagView.measure(spec, spec);

        int height = addTagView.getMeasuredHeight();
        tagsView.setMinimumHeight(height);

        tagsAdapter = new TagsAdapter(sorted, tags);

        MergeRecyclerAdapter adapter = new MergeRecyclerAdapter();
        adapter.addAdapter(SingleViewAdapter.ofView(addTagView));
        adapter.addAdapter(tagsAdapter);
        tagsView.setAdapter(adapter);
    }

    public void setOnDetailClickedListener(OnDetailClickedListener onDetailClickedListener) {
        this.onDetailClickedListener = onDetailClickedListener;
    }

    public void setOnVoteListener(VoteView.OnVoteListener onVoteListener) {
        this.onVoteListener = onVoteListener;
    }

    public void setTagVoteListener(TagVoteListener tagVoteListener) {
        this.tagVoteListener = tagVoteListener;
    }

    public void setOnAddTagClickedListener(Runnable onAddTagClickedListener) {
        this.onAddTagClickedListener = onAddTagClickedListener;
    }

    public boolean isOneHourOld() {
        Instant oneHourAgo = Instant.now().minus(Duration.standardHours(1));
        return feedItem.created().isBefore(oneHourAgo);
    }

    public void addVote(Tag tag, Vote vote) {
        tagsAdapter.updateVote(tag, vote);
    }

    private class TagsAdapter extends RecyclerView.Adapter<TagViewHolder> {
        private final List<Tag> tags;
        private final Map<Tag, Vote> votes;
        private final boolean alwaysVoteViews;
        private int selected = -1;

        private TagsAdapter(List<Tag> tags, Map<Tag, Vote> votes) {
            setHasStableIds(true);
            this.tags = ImmutableList.copyOf(tags);
            this.votes = new HashMap<>(votes);
            this.alwaysVoteViews = settings != null && !settings.hideTagVoteButtons();
        }

        @Override
        public TagViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View view = inflater.inflate(R.layout.tag, parent, false);
            return new TagViewHolder(view);
        }

        @Override
        public void onBindViewHolder(TagViewHolder holder, int position) {
            Tag tag = tags.get(position);
            holder.tag.setText(tag.getTag());
            holder.tag.setOnClickListener(v -> {
                if (onDetailClickedListener != null)
                    onDetailClickedListener.onTagClicked(tag);
            });

            if (tagVoteListener != null && shouldShowVoteView(position)) {
                holder.vote.setVote(votes.get(tag), true);
                holder.vote.setVisibility(View.VISIBLE);

                if (!alwaysVoteViews) {
                    holder.tag.setOnLongClickListener(v -> {
                        updateSelection(-1);
                        return true;
                    });
                }

                holder.vote.setOnVoteListener(vote -> tagVoteListener.onVote(tag, vote));

            } else {
                holder.vote.setVisibility(View.GONE);
                holder.tag.setOnLongClickListener(v -> {
                    updateSelection(position);
                    return true;
                });
            }
        }

        private boolean shouldShowVoteView(int position) {
            return position == selected || alwaysVoteViews;
        }

        private void updateSelection(int position) {
            int previousSelected = selected;
            selected = position;

            notifyItemChanged(Math.max(0, previousSelected));
            notifyItemChanged(selected);
        }

        @Override
        public int getItemCount() {
            return tags.size();
        }

        @Override
        public long getItemId(int position) {
            return tags.get(position).getId();
        }

        public void updateVote(Tag tag, Vote vote) {
            votes.put(tag, vote);
            notifyDataSetChanged();
        }
    }

    private static class TagViewHolder extends RecyclerView.ViewHolder {
        final TextView tag;
        final VoteView vote;

        TagViewHolder(View itemView) {
            super(itemView);
            tag = ButterKnife.findById(itemView, R.id.tag_text);
            vote = ButterKnife.findById(itemView, R.id.tag_vote);
        }
    }

    public interface OnDetailClickedListener {
        /**
         * Called if the user clicked on a tag.
         *
         * @param tag The tag that was clicked.
         */
        void onTagClicked(Tag tag);

        /**
         * Called if a user clicks on a username
         *
         * @param username The username that was clicked.
         */
        void onUserClicked(String username);
    }

    public interface TagVoteListener {
        boolean onVote(Tag tag, Vote vote);
    }
}
