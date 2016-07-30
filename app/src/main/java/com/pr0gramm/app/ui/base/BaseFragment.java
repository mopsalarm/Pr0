package com.pr0gramm.app.ui.base;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.view.View;

import com.f2prateek.dart.Dart;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.Dagger;
import com.trello.rxlifecycle.FragmentEvent;
import com.trello.rxlifecycle.components.support.RxFragment;

import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * A robo fragment that provides lifecycle events as an observable.
 */
public abstract class BaseFragment extends RxFragment {
    private Unbinder unbinder;

    public final <T> AsyncLifecycleTransformer<T> bindUntilEventAsync(@NonNull FragmentEvent event) {
        return new AsyncLifecycleTransformer<>(bindUntilEvent(event));
    }

    public final <T> AsyncLifecycleTransformer<T> bindToLifecycleAsync() {
        return new AsyncLifecycleTransformer<>(bindToLifecycle());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        FragmentActivity activity = getActivity();
        injectComponent(Dagger.activityComponent(activity));

        if (getArguments() != null)
            Dart.inject(this, getArguments());

        super.onCreate(savedInstanceState);
    }

    protected abstract void injectComponent(ActivityComponent activityComponent);

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        unbinder = ButterKnife.bind(this, view);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (unbinder != null) {
            unbinder.unbind();
            unbinder = null;
        }
    }
}
