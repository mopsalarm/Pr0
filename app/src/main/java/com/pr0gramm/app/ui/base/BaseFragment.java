package com.pr0gramm.app.ui.base;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.View;

import com.f2prateek.dart.Dart;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.ab.ExperimentService;
import com.pr0gramm.app.util.BackgroundScheduler;
import com.trello.rxlifecycle.FragmentEvent;
import com.trello.rxlifecycle.RxLifecycle;
import com.trello.rxlifecycle.components.FragmentLifecycleProvider;

import javax.inject.Inject;

import butterknife.ButterKnife;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.subjects.BehaviorSubject;

/**
 * A robo fragment that provides lifecycle events as an observable.
 */
public abstract class BaseFragment extends Fragment implements FragmentLifecycleProvider {
    private final BehaviorSubject<FragmentEvent> lifecycleSubject = BehaviorSubject.create();

    @Inject
    protected ExperimentService experimentService;

    public Observable<FragmentEvent> lifecycle() {
        return lifecycleSubject.asObservable();
    }


    @Override
    public final <T> Observable.Transformer<T, T> bindUntilEvent(FragmentEvent event) {
        return observable -> observable
                .subscribeOn(BackgroundScheduler.instance())
                .unsubscribeOn(BackgroundScheduler.instance())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(RxLifecycle.<T>bindUntilFragmentEvent(lifecycleSubject, event));
    }

    @Override
    public final <T> Observable.Transformer<T, T> bindToLifecycle() {
        return observable -> observable
                .subscribeOn(BackgroundScheduler.instance())
                .unsubscribeOn(BackgroundScheduler.instance())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(RxLifecycle.<T>bindFragment(lifecycleSubject));
    }

    protected final <T> Observable.Transformer<T, T> bindToLifecycleForeground() {
        //noinspection unchecked
        return (Observable.Transformer<T, T>) RxLifecycle.<T>bindFragment(lifecycleSubject);
    }

    @Override
    public void onAttach(android.app.Activity activity) {
        super.onAttach(activity);
        lifecycleSubject.onNext(FragmentEvent.ATTACH);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentActivity activity = getActivity();
        injectComponent(Dagger.activityComponent(activity));

        if (getArguments() != null)
            Dart.inject(this, getArguments());

        lifecycleSubject.onNext(FragmentEvent.CREATE);
    }

    protected abstract void injectComponent(ActivityComponent activityComponent);

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ButterKnife.bind(this, view);
        lifecycleSubject.onNext(FragmentEvent.CREATE_VIEW);
    }

    @Override
    public void onStart() {
        super.onStart();
        lifecycleSubject.onNext(FragmentEvent.START);
    }

    @Override
    public void onResume() {
        super.onResume();
        lifecycleSubject.onNext(FragmentEvent.RESUME);
    }

    @Override
    public void onPause() {
        lifecycleSubject.onNext(FragmentEvent.PAUSE);
        super.onPause();
    }

    @Override
    public void onStop() {
        lifecycleSubject.onNext(FragmentEvent.STOP);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        lifecycleSubject.onNext(FragmentEvent.DESTROY_VIEW);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        lifecycleSubject.onNext(FragmentEvent.DESTROY);
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        lifecycleSubject.onNext(FragmentEvent.DETACH);
        super.onDetach();
    }

    protected <T> Observable.Transformer<T, T> bindView() {
        //noinspection unchecked
        return (Observable.Transformer<T, T>) RxLifecycle.<T>bindUntilFragmentEvent(lifecycle(), FragmentEvent.DESTROY_VIEW);
    }
}
