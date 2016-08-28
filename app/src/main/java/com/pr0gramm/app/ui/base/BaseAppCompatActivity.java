package com.pr0gramm.app.ui.base;

import android.os.Bundle;

import com.f2prateek.dart.Dart;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.Dagger;
import com.trello.rxlifecycle.LifecycleTransformer;
import com.trello.rxlifecycle.android.ActivityEvent;
import com.trello.rxlifecycle.components.support.RxAppCompatActivity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import butterknife.ButterKnife;
import butterknife.Unbinder;
import rx.Observable;

import static com.pr0gramm.app.util.AndroidUtility.checkMainThread;

/**
 * A {@link android.support.v7.app.AppCompatActivity} with dagger injection and stuff.
 */
public abstract class BaseAppCompatActivity extends RxAppCompatActivity {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private ActivityComponent activityComponent;

    private Unbinder unbinder;

    public <T> Observable.Transformer<T, T> bindUntilEventAsync(ActivityEvent event) {
        return new AsyncLifecycleTransformer<>(bindUntilEvent(event));
    }

    public final <T> LifecycleTransformer<T> bindToLifecycleAsync() {
        return new AsyncLifecycleTransformer<>(bindToLifecycle());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        activityComponent = Dagger.newActivityComponent(this);
        injectComponent(activityComponent);

        Dart.inject(this);
        super.onCreate(savedInstanceState);
    }

    protected abstract void injectComponent(ActivityComponent appComponent);

    public ActivityComponent getActivityComponent() {
        checkMainThread();

        if (activityComponent == null)
            activityComponent = Dagger.newActivityComponent(this);

        return activityComponent;
    }

    @Override
    protected void onDestroy() {
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
