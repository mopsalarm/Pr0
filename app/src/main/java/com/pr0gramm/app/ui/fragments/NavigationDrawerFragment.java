package com.pr0gramm.app.ui.fragments;

import android.app.Dialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.UserClasses;
import com.pr0gramm.app.api.categories.ExtraCategoryApi;
import com.pr0gramm.app.api.pr0gramm.response.Info;
import com.pr0gramm.app.services.BookmarkService;
import com.pr0gramm.app.services.Graph;
import com.pr0gramm.app.services.InboxService;
import com.pr0gramm.app.services.SingleShotService;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.FeedbackActivity;
import com.pr0gramm.app.ui.GraphDrawable;
import com.pr0gramm.app.ui.RxRoboFragment;
import com.pr0gramm.app.ui.SettingsActivity;
import com.pr0gramm.app.ui.dialogs.LoginActivity;
import com.pr0gramm.app.ui.dialogs.LogoutDialogFragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import rx.functions.Actions;

import static com.pr0gramm.app.util.AndroidUtility.getStatusBarHeight;

public class NavigationDrawerFragment extends RxRoboFragment {
    private static final Logger logger = LoggerFactory.getLogger(DrawerFragment.class);

    @Inject
    private UserService userService;

    @Inject
    private InboxService inboxService;

    @Inject
    private Settings settings;

    @Inject
    private BookmarkService bookmarkService;

    @Inject
    private SingleShotService singleShotService;

    @Inject
    private ExtraCategoryApi extraCategoryApi;

    private View userImageView;
    private TextView usernameView;
    private TextView userTypeView;
    private TextView benisView;
    private TextView benisDeltaView;
    private View benisContainer;
    private ImageView benisGraph;

    private MenuItem settingsView;
    private MenuItem feedbackView;
    private MenuItem logoutView;
    private MenuItem loginView;

    private final LoginActivity.DoIfAuthorizedHelper doIfAuthorizedHelper = LoginActivity.helper(this);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.navigationdrawer, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        NavigationView navigationView = (NavigationView) view;
        View navigationViewHeader = navigationView.inflateHeaderView(R.layout.navigationdrawer_header);
        Menu menu = navigationView.getMenu();

        userImageView = navigationViewHeader.findViewById(R.id.user_image);
        usernameView = (TextView) navigationViewHeader.findViewById(R.id.username);
        userTypeView = (TextView) navigationViewHeader.findViewById(R.id.user_type);
        benisView = (TextView) navigationViewHeader.findViewById(R.id.kpi_benis);
        benisDeltaView = (TextView) navigationViewHeader.findViewById(R.id.benis_delta);
        benisContainer = navigationViewHeader.findViewById(R.id.benis_container);
        benisGraph = (ImageView) navigationViewHeader.findViewById(R.id.benis_graph);

        settingsView = menu.findItem(R.id.action_settings);
        feedbackView = menu.findItem(R.id.action_feedback);
        logoutView = menu.findItem(R.id.action_logout);
        loginView = menu.findItem(R.id.action_login);

        // add some space on the top for the translucent status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams)
                    userImageView.getLayoutParams();

            params.topMargin += getStatusBarHeight(getActivity());
        }

        benisGraph.setOnClickListener(this::onBenisGraphClicked);

        settingsView.setOnMenuItemClickListener(v -> {
            Intent intent = new Intent(getActivity(), SettingsActivity.class);
            startActivity(intent);
            return true;
        });

        feedbackView.setOnMenuItemClickListener(v -> {
            Intent intent = new Intent(getActivity(), FeedbackActivity.class);
            startActivity(intent);
            return true;
        });

        loginView.setOnMenuItemClickListener(v -> {
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            startActivity(intent);
            return true;
        });

        logoutView.setOnMenuItemClickListener(v -> {
            LogoutDialogFragment fragment = new LogoutDialogFragment();
            fragment.show(getFragmentManager(), null);
            return true;
        });

    }

    @Override
    public void onResume() {
        super.onResume();

        userService.loginState()
                .compose(bindToLifecycle())
                .subscribe(this::onLoginStateChanged, Actions.empty());

        // TODO

        benisGraph.setVisibility(settings.benisGraphEnabled() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        doIfAuthorizedHelper.onActivityResult(requestCode, resultCode);
    }

    private void onBenisGraphClicked(View view) {
        DialogBuilder.start(getActivity())
                .content(R.string.benis_graph_explanation)
                .positive(R.string.okay)
                .show();
    }

    private void onLoginStateChanged(UserService.LoginState state) {
        if (state.isAuthorized()) {
            Info.User user = state.getInfo().getUser();
            usernameView.setText(user.getName());
            usernameView.setOnClickListener(v -> onUsernameClicked());

            userTypeView.setVisibility(View.VISIBLE);
            userTypeView.setTextColor(getResources().getColor(UserClasses.MarkColors.get(user.getMark())));
            userTypeView.setText(getString(UserClasses.MarkStrings.get(user.getMark())).toUpperCase());

            Graph benis = state.getBenisHistory();

            benisView.setText(String.valueOf(user.getScore()));
            benisContainer.setVisibility(View.VISIBLE);
            benisGraph.setImageDrawable(new GraphDrawable(benis));

            if (benis.points().size() > 2) {
                updateBenisDeltaForGraph(benis);
            }

            loginView.setVisible(false);
            logoutView.setVisible(true);
        } else {
            usernameView.setText(R.string.pr0gramm);
            usernameView.setOnClickListener(null);

            userTypeView.setText("");
            userTypeView.setVisibility(View.GONE);

            benisContainer.setVisibility(View.GONE);
            benisGraph.setImageDrawable(null);

            loginView.setVisible(true);
            logoutView.setVisible(false);
        }
    }

    private void onUsernameClicked() {
        // TODO
    }

    private void updateBenisDeltaForGraph(Graph benis) {
        // TODO
    }
}