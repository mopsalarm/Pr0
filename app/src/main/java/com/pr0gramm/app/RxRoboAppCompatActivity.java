package com.pr0gramm.app;

import android.os.Bundle;

import roboguice.activity.RoboActionBarActivity;
import roboguice.activity.RoboFragmentActivity;
import rx.Observable;
import rx.android.lifecycle.LifecycleEvent;
import rx.subjects.BehaviorSubject;

/**
 * A {@link RoboFragmentActivity} that is an {@link android.support.v7.app.AppCompatActivity}
 * with roboguice functionality and its lifecycle exposed as an observable.
 */
public class RxRoboAppCompatActivity extends RoboActionBarActivity {
    private final BehaviorSubject<LifecycleEvent> lifecycleSubject = BehaviorSubject.create();

    public Observable<LifecycleEvent> lifecycle() {
        return lifecycleSubject.asObservable();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        lifecycleSubject.onNext(LifecycleEvent.CREATE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        lifecycleSubject.onNext(LifecycleEvent.START);
    }

    @Override
    protected void onResume() {
        super.onResume();
        lifecycleSubject.onNext(LifecycleEvent.RESUME);
    }

    @Override
    protected void onPause() {
        lifecycleSubject.onNext(LifecycleEvent.PAUSE);
        super.onPause();
    }

    @Override
    protected void onStop() {
        lifecycleSubject.onNext(LifecycleEvent.STOP);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        lifecycleSubject.onNext(LifecycleEvent.DESTROY);
        super.onDestroy();
    }
}
