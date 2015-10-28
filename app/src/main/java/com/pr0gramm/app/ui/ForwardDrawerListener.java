package com.pr0gramm.app.ui;

import android.support.v4.widget.DrawerLayout;
import android.view.View;

/**
 */
public class ForwardDrawerListener implements DrawerLayout.DrawerListener {
    private final DrawerLayout.DrawerListener delegate;

    public ForwardDrawerListener(DrawerLayout.DrawerListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onDrawerSlide(View drawerView, float slideOffset) {
        delegate.onDrawerSlide(drawerView, slideOffset);
    }

    @Override
    public void onDrawerOpened(View drawerView) {
        delegate.onDrawerOpened(drawerView);
    }

    @Override
    public void onDrawerClosed(View drawerView) {
        delegate.onDrawerClosed(drawerView);
    }

    @Override
    public void onDrawerStateChanged(int newState) {
        delegate.onDrawerStateChanged(newState);
    }
}
