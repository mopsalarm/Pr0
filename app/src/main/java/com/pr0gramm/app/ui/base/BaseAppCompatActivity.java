package com.pr0gramm.app.ui.base;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.SupportV4App;
import android.support.v7.app.AppCompatActivity;

import com.f2prateek.dart.Dart;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.Dagger;
import com.trello.rxlifecycle.ActivityEvent;
import com.trello.rxlifecycle.RxLifecycle;
import com.trello.rxlifecycle.components.ActivityLifecycleProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import butterknife.ButterKnife;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.subjects.BehaviorSubject;

import static com.pr0gramm.app.util.AndroidUtility.checkMainThread;

/**
 * A {@link android.support.v7.app.AppCompatActivity}
 * with roboguice functionality and its lifecycle exposed as an observable.
 */
public abstract class BaseAppCompatActivity extends AppCompatActivity implements ActivityLifecycleProvider {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private static final int[] POW_2 = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096};
    // 16 bits available at all
    private static final int CHAIN_BITS_FOR_INDEX = 4; // adjustable constant, use value 3 or 4
    private static final int CHAIN_BITS_COUNT = 12; // adjustable constant, use value 9 or 12
    private static final int CHAIN_INDEX_MASK = ~(0x80000000 >> (31 - CHAIN_BITS_FOR_INDEX));
    // max allowed depth of fragments
    private static final int CHAIN_MAX_DEPTH = CHAIN_BITS_COUNT / CHAIN_BITS_FOR_INDEX;
    // bits for external usage
    private static final int REQUEST_CODE_EXT_BITS = 16 - CHAIN_BITS_COUNT;
    private static final int REQUEST_CODE_MASK = ~(0x80000000 >> (31 - REQUEST_CODE_EXT_BITS));
    // we have to add +1 for every index
    // because we could not determine 0 index at all
    private static final int FRAGMENT_MAX_COUNT = POW_2[CHAIN_BITS_FOR_INDEX] - 1;

    private final BehaviorSubject<ActivityEvent> lifecycleSubject = BehaviorSubject.create();
    private ActivityComponent activityComponent;

    @Override
    public Observable<ActivityEvent> lifecycle() {
        return lifecycleSubject.asObservable();
    }

    @Override
    public final <T> Observable.Transformer<T, T> bindUntilEvent(ActivityEvent event) {
        return observable -> RxLifecycle.<T>bindUntilActivityEvent(lifecycleSubject, event)
                .call(observable.observeOn(AndroidSchedulers.mainThread()));
    }

    @Override
    public final <T> Observable.Transformer<T, T> bindToLifecycle() {
        return observable -> RxLifecycle.<T>bindActivity(lifecycleSubject)
                .call(observable.observeOn(AndroidSchedulers.mainThread()));
    }

    public void startActivityFromFragment(Fragment fragment, Intent intent, int requestCode) {
        if (requestCode == -1) {
            // this is the same as "no request code"
            super.startActivityFromFragment(fragment, intent, requestCode);
            return;
        }

        if ((requestCode & (~REQUEST_CODE_MASK)) != 0) {
            logger.warn("Can use only use lower {} bits for requestCode, int value in range 1..{}",
                    REQUEST_CODE_EXT_BITS, POW_2[REQUEST_CODE_EXT_BITS] - 1);

            throw new IllegalArgumentException("requestCode not in valid range");
        }

        int chain = 0;
        int depth = 0;

        Fragment node = fragment;
        do {
            if (depth > CHAIN_MAX_DEPTH) {
                throw new IllegalStateException("Too deep structure of fragments, max " + CHAIN_MAX_DEPTH);
            }

            int index = SupportV4App.fragmentIndex(node);
            if (index < 0) {
                throw new IllegalStateException("Fragment is out of FragmentManager: " + node);
            }

            if (index >= FRAGMENT_MAX_COUNT) {
                throw new IllegalStateException("Too many fragments inside (max " + FRAGMENT_MAX_COUNT + "): " + node.getParentFragment());
            }

            chain = (chain << CHAIN_BITS_FOR_INDEX) + (index + 1);
            node = node.getParentFragment();
            depth += 1;
        } while (node != null);

        int newCode = (chain << REQUEST_CODE_EXT_BITS) + (requestCode & REQUEST_CODE_MASK);

        super.startActivityForResult(intent, newCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode & 0xffff0000) != 0) {
            logger.warn("Activity result requestCode does not correspond restrictions: 0x{}",
                    Integer.toHexString(requestCode));

            super.onActivityResult(requestCode, resultCode, data);
            return;
        }

        SupportV4App.activityFragmentsNoteStateNotSaved(this);

        int chain = requestCode >>> REQUEST_CODE_EXT_BITS;
        if (chain != 0) {
            List<Fragment> active = SupportV4App.activityFragmentsActive(this);
            Fragment fragment;

            do {
                int index = (chain & CHAIN_INDEX_MASK) - 1;
                if (active == null || index < 0 || index >= active.size()) {
                    logger.error("Activity result fragment chain out of range: 0x{}",
                            Integer.toHexString(requestCode));

                    return;
                }

                fragment = active.get(index);
                if (fragment == null) {
                    break;
                }

                active = SupportV4App.fragmentChildFragmentManagerActive(fragment);
                chain = chain >>> CHAIN_BITS_FOR_INDEX;
            } while (chain != 0);

            if (fragment != null) {
                fragment.onActivityResult(requestCode & REQUEST_CODE_MASK, resultCode, data);
            } else {
                logger.error("Activity result no fragment exists for chain: 0x{}",
                        Integer.toHexString(requestCode));
            }
        }
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
    }

    @Override
    public void onSupportContentChanged() {
        super.onSupportContentChanged();
        ButterKnife.bind(this);
    }
}
