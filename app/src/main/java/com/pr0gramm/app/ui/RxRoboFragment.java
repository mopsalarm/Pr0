package com.pr0gramm.app.ui;

import android.os.Bundle;
import android.view.View;

import com.f2prateek.dart.Dart;
import com.google.inject.Inject;
import com.pr0gramm.app.ab.ExperimentService;
import com.trello.rxlifecycle.FragmentEvent;
import com.trello.rxlifecycle.RxLifecycle;
import com.trello.rxlifecycle.components.FragmentLifecycleProvider;

import butterknife.ButterKnife;
import roboguice.fragment.RoboFragment;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.subjects.BehaviorSubject;

/**
 * A robo fragment that provides lifecycle events as an observable.
 */
public class RxRoboFragment extends RoboFragment implements FragmentLifecycleProvider {
    private final BehaviorSubject<FragmentEvent> lifecycleSubject = BehaviorSubject.create();

    @Inject
    protected ExperimentService experimentService;

    public Observable<FragmentEvent> lifecycle() {
        return lifecycleSubject.asObservable();
    }


    @Override
    public final <T> Observable.Transformer<T, T> bindUntilEvent(FragmentEvent event) {
        return observable -> RxLifecycle.<T>bindUntilFragmentEvent(lifecycleSubject, event)
                .call(observable.observeOn(AndroidSchedulers.mainThread()));
    }

    @Override
    public final <T> Observable.Transformer<T, T> bindToLifecycle() {
        return observable -> RxLifecycle.<T>bindFragment(lifecycleSubject)
                .call(observable.observeOn(AndroidSchedulers.mainThread()));
    }

    @Override
    public void onAttach(android.app.Activity activity) {
        super.onAttach(activity);
        lifecycleSubject.onNext(FragmentEvent.ATTACH);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(getArguments() != null) {
            Dart.inject(this, getArguments());
        }

        lifecycleSubject.onNext(FragmentEvent.CREATE);
    }

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
}
