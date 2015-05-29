package com.pr0gramm.app.ui;

import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;

/**
 */
public class EmptyAdapter extends RecyclerView.Adapter {
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getItemCount() {
        return 0;
    }
}
