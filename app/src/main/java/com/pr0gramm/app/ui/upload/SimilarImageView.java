package com.pr0gramm.app.ui.upload;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.HasThumbnail;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.UriHelper;
import com.squareup.picasso.Picasso;

import java.util.List;

/**
 */
public class SimilarImageView extends RecyclerView {
    public SimilarImageView(Context context) {
        super(context);
        init();
    }

    public SimilarImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SimilarImageView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
    }

    public void setThumbnails(Iterable<HasThumbnail> thumbnails) {
        setAdapter(new ThumbnailAdapter(ImmutableList.copyOf(thumbnails)));
    }

    private class ThumbnailAdapter extends Adapter<ThumbnailViewHolder> {
        private final List<HasThumbnail> thumbnails;
        private final Picasso picasso = Dagger.appComponent(getContext()).picasso();

        ThumbnailAdapter(List<HasThumbnail> thumbnails) {
            this.thumbnails = thumbnails;
        }

        @Override
        public void onBindViewHolder(ThumbnailViewHolder holder, int position) {
            HasThumbnail thumb = thumbnails.get(position);

            Uri imageUri = UriHelper.Companion.of(getContext()).thumbnail(thumb);
            picasso.load(imageUri)
                    .config(Bitmap.Config.RGB_565)
                    .placeholder(new ColorDrawable(0xff333333))
                    .into(holder.image);
        }

        @Override
        public ThumbnailViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.thumbnail, parent, false);

            return new ThumbnailViewHolder(view);
        }

        @Override
        public int getItemCount() {
            return thumbnails.size();
        }
    }

    private static class ThumbnailViewHolder extends ViewHolder {
        final ImageView image;

        public ThumbnailViewHolder(View itemView) {
            super(itemView);
            image = (ImageView) itemView;
        }
    }
}
