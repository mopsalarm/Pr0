package com.pr0gramm.app.ui.bubble;

import android.support.v7.widget.RecyclerView;

import com.jakewharton.rxbinding.internal.MainThreadSubscription;

import rx.Observable;
import rx.Subscriber;

import static com.jakewharton.rxbinding.internal.Preconditions.checkUiThread;

/**
 */
public class RecyclerViewOnScrollSubscription implements Observable.OnSubscribe<Void> {
    private final RecyclerView view;

    private RecyclerViewOnScrollSubscription(RecyclerView view) {
        this.view = view;
    }

    @Override
    public void call(final Subscriber<? super Void> subscriber) {
        checkUiThread();

        final RecyclerView.OnScrollListener listener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (!subscriber.isUnsubscribed()) {
                    subscriber.onNext(null);
                }
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                // empty
            }
        };

        view.addOnScrollListener(listener);

        subscriber.add(new MainThreadSubscription() {
            @Override
            protected void onUnsubscribe() {
                view.removeOnScrollListener(listener);
            }
        });
    }

    public static Observable<Void> onScroll(RecyclerView view) {
        return Observable.create(new RecyclerViewOnScrollSubscription(view));
    }
}
