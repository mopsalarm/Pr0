package com.pr0gramm.app.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.pr0gramm.app.GraphDrawable;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.api.pr0gramm.Info;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.SettingsActivity;
import com.pr0gramm.app.ui.dialogs.LoginDialogFragment;
import com.pr0gramm.app.ui.dialogs.LogoutDialogFragment;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;
import rx.Subscription;
import rx.functions.Actions;

import static rx.android.observables.AndroidObservable.bindFragment;

/**
 */
public class DrawerFragment extends RoboFragment {
    private Map<Integer, TextView> itemViews;

    @Inject
    private UserService userService;

    @InjectView(R.id.username)
    private TextView usernameView;

    @InjectView(R.id.benis)
    private TextView benisView;

    @InjectView(R.id.benis_container)
    private View benisContainer;

    @InjectView(R.id.benis_graph)
    private ImageView benisGraph;

    @InjectView(R.id.action_login)
    private View loginView;

    @InjectView(R.id.action_logout)
    private View logoutView;

    @InjectView(R.id.action_settings)
    private View settingsView;

    @Inject
    private Settings settings;

    private ColorStateList defaultColor = ColorStateList.valueOf(Color.WHITE);
    private ColorStateList markedColor;

    private Subscription subscription;
    private int selected;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = new ContextThemeWrapper(getActivity(), R.style.Theme_AppCompat_Light);
        inflater = LayoutInflater.from(context);
        return inflater.inflate(R.layout.left_drawer, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // get "marked" color
        int primary = getActivity().getResources().getColor(R.color.primary);
        markedColor = ColorStateList.valueOf(primary);

        // initialize views
        itemViews = new HashMap<>();
        for (int action : actions) {
            TextView itemView = (TextView) view.findViewById(action);
            itemViews.put(action, itemView);

            defaultColor = itemView.getTextColors();

            itemView.setOnClickListener(v -> onActionClicked(action));
        }

        select(selected);

        settingsView.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), SettingsActivity.class);
            startActivity(intent);
        });

        loginView.setOnClickListener(v -> {
            LoginDialogFragment dialog = new LoginDialogFragment();
            dialog.show(getFragmentManager(), null);
        });

        logoutView.setOnClickListener(v -> {
            LogoutDialogFragment fragment = new LogoutDialogFragment();
            fragment.show(getFragmentManager(), null);
        });

        benisGraph.setOnClickListener(this::onBenisGraphClicked);
    }

    private void onBenisGraphClicked(View view) {
        new MaterialDialog.Builder(getActivity())
                .content(R.string.benis_graph_explanation)
                .positiveText(R.string.okay)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();

        subscription = bindFragment(this, userService.getLoginStateObservable())
                .subscribe(this::onLoginStateChanged, Actions.empty());

        benisGraph.setVisibility(settings.benisGraphEnabled() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onPause() {
        if (subscription != null)
            subscription.unsubscribe();

        super.onPause();
    }

    private void deselect() {
        for (TextView view : itemViews.values())
            view.setTextColor(defaultColor);
    }

    public void select(int action) {
        selected = action;

        // stop if not yet initialized
        if (itemViews == null)
            return;

        deselect();

        TextView view = itemViews.get(action);
        if (view != null)
            view.setTextColor(markedColor);
    }

    private void onActionClicked(int id) {
        select(id);

        if (getActivity() instanceof OnDrawerActionListener)
            ((OnDrawerActionListener) getActivity()).onActionClicked(id);
    }

    private void onLoginStateChanged(UserService.LoginState state) {
        if (state.isAuthorized()) {
            Info.User user = state.getInfo().getUser();
            usernameView.setText(user.getName());

            String benisValue = String.valueOf(user.getScore());

            benisView.setText(benisValue);
            benisContainer.setVisibility(View.VISIBLE);
            benisGraph.setImageDrawable(new GraphDrawable(state.getBenisHistory()));

            loginView.setVisibility(View.GONE);
            logoutView.setVisibility(View.VISIBLE);
        } else {
            usernameView.setText(R.string.pr0gramm);
            benisContainer.setVisibility(View.GONE);
            benisGraph.setImageDrawable(null);

            loginView.setVisibility(View.VISIBLE);
            logoutView.setVisibility(View.GONE);
        }
    }

    public interface OnDrawerActionListener {
        /**
         * Called if a drawer action was clicked.
         *
         * @param action The action id that was clicked.
         */
        void onActionClicked(int action);
    }


    private final int[] actions = {
            R.id.action_feed_promoted,
            R.id.action_feed_new,
            R.id.action_upload,
            R.id.action_messages,
            R.id.action_favorites
    };
}
