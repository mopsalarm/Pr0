package com.pr0gramm.app.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 */
public class SingleViewAdapter<V extends View> extends SingleValueAdapter<Void, V> {
    private final ViewFactory<V> viewFactory;

    public SingleViewAdapter(ViewFactory<V> viewFactory) {
        super(null);
        this.viewFactory = viewFactory;
    }

    @Override
    protected V createView(Context context, ViewGroup parent) {
        return viewFactory.newView(context);
    }

    @Override
    protected void bindView(V view, Void object) {
    }

    public interface ViewFactory<V> {
        V newView(Context context);
    }

    public static <V extends View> SingleViewAdapter<V> ofView(V view) {
        return new SingleViewAdapter<>(context -> view);
    }

    public static <V extends View> SingleViewAdapter<V> of(ViewFactory<V> factory) {
        return new SingleViewAdapter<>(factory);
    }
}
