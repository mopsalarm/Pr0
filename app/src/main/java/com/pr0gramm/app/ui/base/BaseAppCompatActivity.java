package com.pr0gramm.app.ui.base;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;

import com.f2prateek.dart.Dart;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.util.BackgroundScheduler;
import com.trello.rxlifecycle.ActivityEvent;
import com.trello.rxlifecycle.ActivityLifecycleProvider;
import com.trello.rxlifecycle.RxLifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import butterknife.ButterKnife;
import butterknife.Unbinder;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.subjects.BehaviorSubject;

import static com.pr0gramm.app.util.AndroidUtility.checkMainThread;

/**
 * A {@link android.support.v7.app.AppCompatActivity} with dagger injection and stuff.
 */
public abstract class BaseAppCompatActivity extends AppCompatActivity implements ActivityLifecycleProvider {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final BehaviorSubject<ActivityEvent> lifecycleSubject = BehaviorSubject.create();
    private ActivityComponent activityComponent;

    private Unbinder unbinder;

    @NonNull
    @Override
    public Observable<ActivityEvent> lifecycle() {
        return lifecycleSubject.asObservable();
    }

    @Override
    public final <T> Observable.Transformer<T, T> bindUntilEvent(@NonNull ActivityEvent event) {
        return observable -> observable.compose(RxLifecycle.bindUntilEvent(lifecycleSubject, event));
    }

    public <T> Observable.Transformer<T, T> bindUntilEventAsync(ActivityEvent event) {
        return observable -> observable
                .subscribeOn(BackgroundScheduler.instance())
                .unsubscribeOn(BackgroundScheduler.instance())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindUntilEvent(event));
    }

    @Override
    public final <T> Observable.Transformer<T, T> bindToLifecycle() {
        return observable -> observable
                .subscribeOn(BackgroundScheduler.instance())
                .unsubscribeOn(BackgroundScheduler.instance())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(RxLifecycle.<T>bindActivity(lifecycleSubject));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        activityComponent = Dagger.newActivityComponent(this);

        injectComponent(activityComponent);

        Dart.inject(this);
        lifecycleSubject.onNext(ActivityEvent.CREATE);
    }

    protected abstract void injectComponent(ActivityComponent appComponent);

    public ActivityComponent getActivityComponent() {
        checkMainThread();

        if (activityComponent == null)
            activityComponent = Dagger.newActivityComponent(this);

        return activityComponent;
    }

    @Override
    protected void onStart() {
        super.onStart();
        lifecycleSubject.onNext(ActivityEvent.START);
    }

    @Override
    protected void onResume() {
        super.onResume();
        lifecycleSubject.onNext(ActivityEvent.RESUME);
    }

    @Override
    protected void onPause() {
        lifecycleSubject.onNext(ActivityEvent.PAUSE);
        super.onPause();
    }

    @Override
    protected void onStop() {
        lifecycleSubject.onNext(ActivityEvent.STOP);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        lifecycleSubject.onNext(ActivityEvent.DESTROY);
        super.onDestroy();

        if (unbinder != null) {
            unbinder.unbind();
            unbinder = null;
        }
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        unbinder = ButterKnife.bind(this);
    }
}
