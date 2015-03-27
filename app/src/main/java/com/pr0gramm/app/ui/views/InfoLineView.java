package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.common.collect.Ordering;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.response.Tag;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.Vote;

import java.util.List;

import rx.Observable;
import rx.functions.Actions;

import static com.pr0gramm.app.AndroidUtility.checkMainThread;
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
    private final TextView voteFavoriteView;
    private final TextView addTagView;

    private OnDetailClickedListener onDetailClickedListener;
    private VoteView.OnVoteListener onVoteListener;

    private FeedItem feedItem;

    public InfoLineView(Context context) {
        this(context, null);
    }

    public InfoLineView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InfoLineView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        inflate(context, R.layout.post_info_line, this);

        setOrientation(VERTICAL);

        // get the views from the hierarchy
        ratingView = (TextView) findViewById(R.id.rating);
        usernameView = (UsernameView) findViewById(R.id.username);
        dateView = (TextView) findViewById(R.id.date);
        voteView = (VoteView) findViewById(R.id.voting);
        voteFavoriteView = (TextView) findViewById(R.id.favorite);
        addTagView = (TextView) findViewById(R.id.add_tag);

        tagsView = (RecyclerView) findViewById(R.id.tags);
        tagsView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));

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
    }

    /**
     * Displays the given {@link com.pr0gramm.app.feed.FeedItem} along with
     * the given vote.
     *
     * @param item The item to display
     * @param vote The vote that belongs to the given item.
     */
    public void setFeedItem(FeedItem item, Observable<Vote> vote) {
        this.feedItem = item;

        // update the views!
        usernameView.setUsername(item.getUser(), item.getMark());
        dateView.setText(getRelativeTimeSpanString(getContext(), item.getCreated()));
        updateViewState(Vote.NEUTRAL);

        usernameView.setOnClickListener(v -> {
            if(onDetailClickedListener != null) {
                String username = item.getUser();
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

        int rating = feedItem.getUp() - feedItem.getDown() + min(1, vote.getVoteValue());
        ratingView.setText(String.valueOf(rating));

        voteFavoriteView.setTextColor(vote == Vote.FAVORITE
                ? voteView.getMarkedColor()
                : voteView.getDefaultColor());
    }

    public VoteView getVoteView() {
        return voteView;
    }

    public void setTags(List<Tag> tags) {
        List<Tag> sorted = Ordering.natural().reverse()
                .onResultOf(Tag::getConfidence)
                .sortedCopy(tags);

        tagsView.setAdapter(new TagsAdapter(sorted));
    }

    public OnDetailClickedListener getOnDetailClickedListener() {
        return onDetailClickedListener;
    }

    public void setOnDetailClickedListener(OnDetailClickedListener onDetailClickedListener) {
        this.onDetailClickedListener = onDetailClickedListener;
    }

    public VoteView.OnVoteListener getOnVoteListener() {
        return onVoteListener;
    }

    public void setOnVoteListener(VoteView.OnVoteListener onVoteListener) {
        this.onVoteListener = onVoteListener;
    }

    public TextView getAddTagView() {
        return addTagView;
    }

    private class TagsAdapter extends RecyclerView.Adapter<TagViewHolder> {
        private final List<Tag> tags;

        private TagsAdapter(List<Tag> tags) {
            this.tags = tags;
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
        }

        @Override
        public int getItemCount() {
            return tags.size();
        }
    }

    private static class TagViewHolder extends RecyclerView.ViewHolder {
        final TextView tag;

        TagViewHolder(View itemView) {
            super(itemView);
            tag = (TextView) itemView;
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
         * @param username The username that was clicked.
         */
        void onUserClicked(String username);
    }

}
