package com.pr0gramm.app.ui;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

/**
 */
public abstract class SingleValueAdapter<T, V extends View> extends RecyclerView.Adapter<SingleValueAdapter.Holder<V>> {
    private final T object;

    public SingleValueAdapter(T object) {
        this.object = object;
    }

    @Override
    public final Holder<V> onCreateViewHolder(ViewGroup parent, int viewType) {
        return new Holder<>(createView(parent.getContext(), parent));
    }

    @Override
    public final void onBindViewHolder(Holder<V> view, int position) {
        bindView(view.view, object);
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    protected abstract V createView(Context context, ViewGroup parent);

    protected abstract void bindView(V view, T object);


    public static class Holder<V extends View> extends RecyclerView.ViewHolder {
        final V view;

        public Holder(V itemView) {
            super(itemView);
            this.view = itemView;
        }
    }
}
