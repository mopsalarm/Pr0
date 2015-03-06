package com.pr0gramm.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.common.collect.Ordering;
import com.pr0gramm.app.api.Post;
import com.pr0gramm.app.feed.FeedItem;

import java.util.List;

import static net.danlew.android.joda.DateUtils.getRelativeTimeSpanString;

/**
 */
@SuppressLint("ViewConstructor")
public class InfoLineView extends LinearLayout {
    private final VoteView voteView;
    private final TextView ratingView;
    private final TextView dateView;
    private final UsernameView usernameView;
    private final RecyclerView tagsView;

    private FeedItem feedItem;

    public InfoLineView(Context context, FeedItem feedItem) {
        super(context);
        inflate(context, R.layout.post_info_line, this);
        setOrientation(VERTICAL);

        voteView = (VoteView) findViewById(R.id.voting);
        ratingView = (TextView) findViewById(R.id.rating);
        usernameView = (UsernameView) findViewById(R.id.username);
        dateView = (TextView) findViewById(R.id.date);

        tagsView = (RecyclerView) findViewById(R.id.tags);
        tagsView.setLayoutManager(new LinearLayoutManager(context,
                LinearLayoutManager.HORIZONTAL, false));
        setFeedItem(feedItem);
        voteView.setInfoLineView(this);
    }

    public void setFeedItem(FeedItem item) {
        this.feedItem = item;

        usernameView.setUsername(item.getUser(), item.getMark());
        ratingView.setText(String.valueOf(item.getUp() - item.getDown()));
        dateView.setText(getRelativeTimeSpanString(getContext(), item.getCreated()));

        // TODO we need the voting from the database here.
        // voteView.setVote(item.getVote());
    }
    public void VoteUP(){

        ratingView.setText(String.valueOf((feedItem.getUp() - feedItem.getDown())+1));
    }
    public void VoteDown(){

        ratingView.setText(String.valueOf((feedItem.getUp() - feedItem.getDown())-1));
    }

    public void setTags(List<Post.Tag> tags) {
        List<Post.Tag> sorted = Ordering.natural().reverse()
                .onResultOf(Post.Tag::getConfidence)
                .sortedCopy(tags);

        tagsView.setAdapter(new TagsAdapter(sorted));
    }

    public UsernameView getUsernameView() {
        return usernameView;
    }

    public TextView getRatingView() {
        return ratingView;
    }

    public VoteView getVoteView() {
        return voteView;
    }

    public FeedItem getFeedItem() {
        return feedItem;
    }

    private class TagsAdapter extends RecyclerView.Adapter<TagViewHolder> {
        private final List<Post.Tag> tags;

        private TagsAdapter(List<Post.Tag> tags) {
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
            Post.Tag tag = tags.get(position);
            holder.tag.setText(tag.getTag());
            holder.tag.setOnClickListener(v -> {
                onTagClicked(tag);
            });
        }

        @Override
        public int getItemCount() {
            return tags.size();
        }
    }



    protected void onTagClicked(Post.Tag tag) {

    }

    private static class TagViewHolder extends RecyclerView.ViewHolder {
        final TextView tag;

        TagViewHolder(View itemView) {
            super(itemView);
            tag = (TextView) itemView;
        }
    }

}
