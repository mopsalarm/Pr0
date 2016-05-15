package com.pr0gramm.app.ui;


import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import rx.Observable;
import rx.subjects.PublishSubject;

public class RecyclerItemClickListener {
    private final RecyclerView recyclerView;
    private final GestureDetector mGestureDetector;
    private boolean longPressTriggered;

    private final Listener touchListener = new Listener();
    private final ScrollListener scrollListener = new ScrollListener();

    private final PublishSubject<View> itemClicked = PublishSubject.create();
    private final PublishSubject<View> itemLongClicked = PublishSubject.create();
    private final PublishSubject<Void> itemLongClickedEnded = PublishSubject.create();
    private boolean longClickEnabled;

    public RecyclerItemClickListener(Context context, RecyclerView recyclerView) {
        this.recyclerView = recyclerView;

        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (longClickEnabled) {
                    View childView = recyclerView.findChildViewUnder(e.getX(), e.getY());
                    if (childView != null) {
                        longPressTriggered = true;
                        itemLongClicked.onNext(childView);
                    }
                }
            }
        });

        recyclerView.addOnItemTouchListener(touchListener);
        recyclerView.addOnScrollListener(scrollListener);
    }

    public void destroy() {
        recyclerView.removeOnScrollListener(scrollListener);
        recyclerView.removeOnItemTouchListener(touchListener);
    }

    public Observable<View> itemClicked() {
        return itemClicked.asObservable();
    }

    public Observable<View> itemLongClicked() {
        return itemLongClicked.asObservable();
    }

    public Observable<Void> itemLongClickEnded() {
        return itemLongClickedEnded.asObservable();
    }

    public void enableLongClick(boolean enabled) {
        longClickEnabled = enabled;
        longPressTriggered &= enabled;
    }

    private class Listener extends RecyclerView.SimpleOnItemTouchListener {
        @Override
        public boolean onInterceptTouchEvent(RecyclerView view, MotionEvent e) {
            View childView = view.findChildViewUnder(e.getX(), e.getY());

            if (childView != null && mGestureDetector.onTouchEvent(e)) {
                itemClicked.onNext(childView);
            }

            // a long press might have been triggered between the last touch event
            // and the current. we use this info to start tracking the current touch
            // if that happened.
            boolean intercept = longClickEnabled
                    && longPressTriggered
                    && (e.getAction() != MotionEvent.ACTION_DOWN);

            longPressTriggered = false;

            if (intercept) {
                // actually this click right now might have triggered the long press
                onTouchEvent(view, e);
            }

            return intercept;
        }

        @Override
        public void onTouchEvent(RecyclerView view, MotionEvent motionEvent) {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_HOVER_EXIT:
                case MotionEvent.ACTION_POINTER_UP:
                    itemLongClickedEnded.onNext(null);
                    break;
            }
        }
    }

    private class ScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            switch (newState) {
                case RecyclerView.SCROLL_STATE_DRAGGING:
                case RecyclerView.SCROLL_STATE_SETTLING:
                    recyclerView.removeOnItemTouchListener(touchListener);
                    break;

                case RecyclerView.SCROLL_STATE_IDLE:
                    // ensure that the listener is only added once
                    recyclerView.removeOnItemTouchListener(touchListener);
                    recyclerView.addOnItemTouchListener(touchListener);
                    break;
            }
        }
    }
}
