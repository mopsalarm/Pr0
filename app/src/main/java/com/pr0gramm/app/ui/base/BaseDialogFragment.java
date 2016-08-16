package com.pr0gramm.app.ui.base;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import com.f2prateek.dart.Dart;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.ui.dialogs.DialogDismissListener;
import com.trello.rxlifecycle.LifecycleTransformer;
import com.trello.rxlifecycle.components.support.RxAppCompatDialogFragment;

import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * A robo fragment that provides lifecycle events as an observable.
 */
public abstract class BaseDialogFragment extends RxAppCompatDialogFragment {
    private Unbinder unbinder;

    public final <T> LifecycleTransformer<T> bindToLifecycleAsync() {
        return new AsyncLifecycleTransformer<>(bindToLifecycle());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        injectComponent(Dagger.activityComponent(getActivity()));

        if (getArguments() != null)
            Dart.inject(this, getArguments());

        super.onCreate(savedInstanceState);
    }

    protected abstract void injectComponent(ActivityComponent activityComponent);

    @Override
    public void onStart() {
        super.onStart();

        // bind dialog. It is only created in on start.
        Dialog dialog = getDialog();
        if (dialog != null) {
            unbinder = ButterKnife.bind(this, dialog);
            onDialogViewCreated();
        }
    }

    protected void onDialogViewCreated() {
    }

    @Override
    public void onDestroyView() {
        if (getDialog() != null && getRetainInstance())
            getDialog().setDismissMessage(null);

        super.onDestroyView();

        if (unbinder != null) {
            unbinder.unbind();
            unbinder = null;
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);

        FragmentActivity activity = getActivity();
        if (activity instanceof DialogDismissListener) {
            // propagate to fragment
            ((DialogDismissListener) activity).onDialogDismissed(this);
        }
    }

    protected Context getThemedContext() {
        Dialog dialog = getDialog();
        return dialog != null ? dialog.getContext() : getContext();
    }

    @Override
    public void dismiss() {
        try {
            super.dismiss();
        } catch (Exception ignored) {
            // i never want that!
        }
    }

    @Override
    public void dismissAllowingStateLoss() {
        try {
            super.dismissAllowingStateLoss();
        } catch (Exception ignored) {
            // i never want that!
        }
    }

    public void dismissNow() {
        try {
            dismiss();
            getFragmentManager().executePendingTransactions();
        } catch (Exception err) {
            // woot!?
        }
    }
}
