package com.pr0gramm.app.ui.search;

import android.app.Dialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.WindowManager;
import android.widget.TextView;

import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.Track;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.Truss;
import com.pr0gramm.app.ui.base.BaseDialogFragment;
import com.pr0gramm.app.util.CustomTabsHelper;
import com.trello.rxlifecycle.FragmentEvent;

import butterknife.BindView;
import rx.Observable;


/**
 */
public class SearchConfigDialog extends BaseDialogFragment {
    @Nullable
    private String currentQuery;

    @BindView(R.id.search_query)
    TextView searchQueryView;

    @BindView(R.id.search_config)
    SearchConfigView searchConfigView;

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return DialogBuilder.start(getActivity())
                .layout(R.layout.search_config_dialog)
                .positive(R.string.action_search_simple, this::startSearch)
                .negative(R.string.cancel)
                .neutral(R.string.help, this::openHelpPage)
                .build();
    }

    void startSearch() {
        if (currentQuery != null) {
            Fragment parentFragment = getParentFragment();
            if (parentFragment instanceof SearchListener) {
                // dismiss the dialog and commit the transaction,
                // so that it really is closed
                dismissNow();

                ((SearchListener) parentFragment).performSearch("? " + currentQuery);
            }

            Track.advancedSearchWithDialog(currentQuery);
        }
    }

    @Override
    protected void onDialogViewCreated() {
        getDialog().getWindow().setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT);

        Observable<String> searchTermOrNull = Observable.combineLatest(
                searchConfigView.valid().startWith(false),
                searchConfigView.queryString().startWith(""),
                (valid, query) -> valid ? query : null);

        searchTermOrNull
                .compose(bindUntilEvent(FragmentEvent.DESTROY_VIEW))
                .subscribe(this::updateQueryString);

        // show the "or/and" by default.
        searchConfigView.switchToOperatorSearchType();
        searchConfigView.focus();
    }

    private void updateQueryString(@Nullable String queryString) {
        this.currentQuery = queryString;

        // enable or disable the button
        ((AlertDialog) getDialog())
                .getButton(DialogInterface.BUTTON_POSITIVE)
                .setEnabled(queryString != null);

        if (queryString == null) {

            searchQueryView.setText(R.string.search_query_invalid);
        } else {
            searchQueryView.setText(new Truss()
                    .append(getString(R.string.search_query_query), Truss.bold())
                    .append(" ")
                    .append(queryString)
                    .build());
        }
    }

    private void openHelpPage() {
        Uri uri = Uri.parse("https://github.com/mopsalarm/pr0gramm-tags/blob/master/README.md#userscript");
        new CustomTabsHelper(getActivity()).openCustomTab(uri);
    }
}
