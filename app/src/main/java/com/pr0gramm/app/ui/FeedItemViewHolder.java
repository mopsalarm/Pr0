package com.pr0gramm.app.ui;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;

import com.pr0gramm.app.R;

import static butterknife.ButterKnife.findById;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * View holder for one feed item.
 */
public final class FeedItemViewHolder extends RecyclerView.ViewHolder {
    public final ImageView seen;
    public final ImageView repost;
    public final View preloaded;
    public final ImageView image;

    public FeedItemViewHolder(View itemView) {
        super(itemView);

        image = checkNotNull(findById(itemView, R.id.image));
        seen = checkNotNull(findById(itemView, R.id.seen));
        repost = checkNotNull(findById(itemView, R.id.repost));
        preloaded = checkNotNull(findById(itemView, R.id.preloaded));
    }

    public void setIsRepost() {
        repost.setVisibility(View.VISIBLE);
        seen.setVisibility(View.GONE);
    }

    public void setIsSeen() {
        seen.setVisibility(View.VISIBLE);
        repost.setVisibility(View.GONE);
    }

    public void clear() {
        seen.setVisibility(View.GONE);
        repost.setVisibility(View.GONE);
    }

    public void setIsPreloaded(boolean isPreloaded) {
        preloaded.setVisibility(isPreloaded ? View.VISIBLE : View.GONE);
    }
}
