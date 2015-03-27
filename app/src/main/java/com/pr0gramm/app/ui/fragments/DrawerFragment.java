package com.pr0gramm.app.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.pr0gramm.app.GraphDrawable;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.WrapContentLinearLayoutManager;
import com.pr0gramm.app.api.pr0gramm.Info;
import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.feed.FeedType;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.FeedFilterFormatter;
import com.pr0gramm.app.ui.SettingsActivity;
import com.pr0gramm.app.ui.dialogs.LoginDialogFragment;
import com.pr0gramm.app.ui.dialogs.LogoutDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectResource;
import roboguice.inject.InjectView;
import rx.Subscription;
import rx.functions.Actions;

import static com.google.common.base.Objects.equal;
import static com.google.common.base.Predicates.not;
import static rx.android.observables.AndroidObservable.bindFragment;

/**
 */
public class DrawerFragment extends RoboFragment {
    @Inject
    private UserService userService;

    @Inject
    private Settings settings;

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

    @InjectView(R.id.drawer_nav_list)
    private RecyclerView navItemsRecyclerView;

    @InjectResource(R.drawable.ic_black_action_favorite)
    private Drawable iconFavorites;

    @InjectResource(R.drawable.ic_black_action_home)
    private Drawable iconFeedTypePromoted;

    @InjectResource(R.drawable.ic_black_action_line_chart)
    private Drawable iconFeedTypeNew;

    private final NavigationAdapter navigationAdapter = new NavigationAdapter();

    private ColorStateList defaultColor = ColorStateList.valueOf(Color.BLACK);
    private ColorStateList markedColor;
    private Subscription subscription;


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

        // initialize the top navigation items
        navItemsRecyclerView.setAdapter(navigationAdapter);
        navItemsRecyclerView.setLayoutManager(new WrapContentLinearLayoutManager(
                getActivity(), WrapContentLinearLayoutManager.VERTICAL, false));

        // add the static items to the navigation
        navigationAdapter.setFixedItems(getFixedNavigationItems(Optional.<Info.User>absent()));

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

    /**
     * Adds the default "fixed" items to the menu
     */
    private List<NavigationItem> getFixedNavigationItems(Optional<Info.User> userInfo) {
        List<NavigationItem> items = new ArrayList<>();
        items.add(new NavigationItem(
                new FeedFilter().withFeedType(FeedType.PROMOTED),
                getString(R.string.action_feed_type_promoted),
                iconFeedTypePromoted));

        items.add(new NavigationItem(
                new FeedFilter().withFeedType(FeedType.NEW),
                getString(R.string.action_feed_type_new),
                iconFeedTypeNew));

        if (userInfo.isPresent()) {
            String name = userInfo.get().getName();
            items.add(new NavigationItem(
                    new FeedFilter().withFeedType(FeedType.NEW).withLikes(name),
                       getString(R.string.action_favorites),
                    iconFavorites));
        }

        return items;
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

    /**
     * Fakes the drawable tint by applying a color filter on all compound
     * drawables of this view.
     *
     * @param view  The view to "tint"
     * @param color The color with which the drawables are to be tinted.
     */
    private static void changeCompoundDrawableColor(TextView view, ColorStateList color) {
        int defaultColor = color.getDefaultColor();
        Drawable[] drawables = view.getCompoundDrawables();
        for (Drawable drawable : drawables) {
            if (drawable == null)
                continue;

            // fake the tint with a color filter.
            drawable.mutate().setColorFilter(defaultColor, PorterDuff.Mode.SRC_IN);
        }
    }

    private void onLoginStateChanged(UserService.LoginState state) {
        List<NavigationItem> items;
        if (state.isAuthorized()) {
            Info.User user = state.getInfo().getUser();
            usernameView.setText(user.getName());

            String benisValue = String.valueOf(user.getScore());

            benisView.setText(benisValue);
            benisContainer.setVisibility(View.VISIBLE);
            benisGraph.setImageDrawable(new GraphDrawable(state.getBenisHistory()));

            loginView.setVisibility(View.GONE);
            logoutView.setVisibility(View.VISIBLE);

            items = getFixedNavigationItems(Optional.of(user));
        } else {
            usernameView.setText(R.string.pr0gramm);
            benisContainer.setVisibility(View.GONE);
            benisGraph.setImageDrawable(null);

            loginView.setVisibility(View.VISIBLE);
            logoutView.setVisibility(View.GONE);

            items = getFixedNavigationItems(Optional.<Info.User>absent());
        }

        // update the navigation
        navigationAdapter.setFixedItems(items);
    }

    public void updateCurrentFilters(List<FeedFilter> filters, FeedFilter current) {
        navigationAdapter.updateCurrentFilters(filters, current);
    }

    public interface OnFeedFilterSelected {
        /**
         * Called if a drawer filter was clicked.
         *
         * @param filter The feed filter that was clicked.
         */
        void onFeedFilterSelected(FeedFilter filter);
    }

    private void onFeedFilterClicked(FeedFilter filter) {
        if (getActivity() instanceof OnFeedFilterSelected) {
            ((OnFeedFilterSelected) getActivity()).onFeedFilterSelected(filter);
        }
    }

    private class NavigationAdapter extends RecyclerView.Adapter<NavigationItemViewHolder> {
        private final List<NavigationItem> fixedItems = new ArrayList<>();
        private final List<NavigationItem> allItems = new ArrayList<>();
        private final List<FeedFilter> dynamicFilters = new ArrayList<>();
        private FeedFilter currentFilter;

        @Override
        public NavigationItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater
                    .from(parent.getContext())
                    .inflate(R.layout.left_drawer_nav_item, parent, false);

            return new NavigationItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(NavigationItemViewHolder holder, int position) {
            NavigationItem item = allItems.get(position);
            holder.text.setText(item.title);

            // set the icon of the image
            holder.text.setCompoundDrawablesWithIntrinsicBounds(item.icon, null, null, null);

            // update color
            ColorStateList color = equal(item.filter, currentFilter) ? markedColor : defaultColor;
            holder.text.setTextColor(color);
            changeCompoundDrawableColor(holder.text, color);

            // handle clicks
            holder.itemView.setOnClickListener(v -> onFeedFilterClicked(item.filter));
        }

        @Override
        public int getItemCount() {
            return allItems.size();
        }

        public void setFixedItems(List<NavigationItem> fixedItems) {
            this.fixedItems.clear();
            this.fixedItems.addAll(fixedItems);
            merge();
        }

        public void updateCurrentFilters(List<FeedFilter> filters, FeedFilter current) {
            currentFilter = current;
            dynamicFilters.clear();
            dynamicFilters.addAll(filters);
            merge();
        }

        private void merge() {
            allItems.clear();
            Iterables.addAll(allItems, fixedItems);

            // create a set of all the fixed items, so we wont add them twice
            Set<FeedFilter> fixedItemFilters = FluentIterable.from(fixedItems)
                    .transform(item -> item.filter)
                    .toSet();

            // add the dynamic stuff too
            FluentIterable.from(dynamicFilters)
                    .filter(not(fixedItemFilters::contains))
                    .transform(this::filterToNavItem)
                    .copyInto(allItems);

            notifyDataSetChanged();
        }

        private NavigationItem filterToNavItem(FeedFilter filter) {
            String title = FeedFilterFormatter.format(getActivity(), filter);
            Drawable icon = iconFeedTypePromoted.getConstantState().newDrawable();
            return new NavigationItem(filter, title, icon);
        }
    }

    private class NavigationItemViewHolder extends RecyclerView.ViewHolder {
        final TextView text;

        public NavigationItemViewHolder(View itemView) {
            super(itemView);
            text = (TextView) itemView;
        }
    }

    private class NavigationItem {
        final String title;
        final FeedFilter filter;
        final Drawable icon;

        NavigationItem(FeedFilter filter, String title, Drawable icon) {
            this.title = title;
            this.filter = filter;
            this.icon = icon;
        }
    }
}
