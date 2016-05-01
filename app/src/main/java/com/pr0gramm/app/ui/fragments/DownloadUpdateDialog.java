package com.pr0gramm.app.ui.fragments;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.widget.ProgressBar;

import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.DownloadService;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.base.BaseDialogFragment;
import com.trello.rxlifecycle.FragmentEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import butterknife.BindView;
import rx.Observable;

import static com.trello.rxlifecycle.RxLifecycle.bindUntilFragmentEvent;

/**
 * This dialog shows the progress while downloading something.
 */
public class DownloadUpdateDialog extends BaseDialogFragment {
    private static final Logger logger = LoggerFactory.getLogger("DownloadDialog");
    private final Observable<DownloadService.Status> progress;

    @BindView(R.id.progress)
    ProgressBar progressBar;

    public DownloadUpdateDialog() {
        this(Observable.empty());
    }

    @SuppressLint("ValidFragment")
    public DownloadUpdateDialog(Observable<DownloadService.Status> progress) {
        logger.info("New instance of DownloadUpdateDialog");
        setRetainInstance(true);
        this.progress = progress;
    }

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = DialogBuilder.start(getActivity())
                .layout(R.layout.progress_update)
                .show();

        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    @Override
    protected void onDialogViewCreated() {
        progressBar.setIndeterminate(true);
        progressBar.setMax(1000);
        progress.compose(bindUntilFragmentEvent(lifecycle(), FragmentEvent.DESTROY_VIEW))
                .onErrorResumeNext(Observable.empty())
                .doAfterTerminate(this::dismiss)
                .subscribe(this::updateStatus);
    }

    private void updateStatus(DownloadService.Status status) {
        progressBar.setIndeterminate(status.progress < 0);
        progressBar.setProgress((int) (1000 * status.progress));
    }
}
