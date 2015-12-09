package com.pr0gramm.app.ui.fragments;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.UserClasses;
import com.pr0gramm.app.api.pr0gramm.response.Info;
import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.orm.Bookmark;
import com.pr0gramm.app.services.BookmarkService;
import com.pr0gramm.app.services.Graph;
import com.pr0gramm.app.services.NavigationProvider;
import com.pr0gramm.app.services.NavigationProvider.NavigationItem;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.FeedbackActivity;
import com.pr0gramm.app.ui.GraphDrawable;
import com.pr0gramm.app.ui.InboxActivity;
import com.pr0gramm.app.ui.InboxType;
import com.pr0gramm.app.ui.SettingsActivity;
import com.pr0gramm.app.ui.UploadActivity;
import com.pr0gramm.app.ui.WrapContentLinearLayoutManager;
import com.pr0gramm.app.ui.base.BaseFragment;
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment;
import com.pr0gramm.app.ui.dialogs.LoginActivity;
import com.pr0gramm.app.ui.dialogs.LogoutDialogFragment;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.ButterKnife;
import rx.functions.Actions;

import static com.google.common.base.Objects.equal;
import static com.pr0gramm.app.R.id.unread_count;
import static com.pr0gramm.app.util.AndroidUtility.getStatusBarHeight;
import static com.pr0gramm.app.util.AndroidUtility.ifPresent;

/**
 */
public class DrawerFragment extends BaseFragment {
    @Inject
    UserService userService;

    @Inject
    Settings settings;

    @Inject
    BookmarkService bookmarkService;

    @Inject
    NavigationProvider navigationProvider;

    @Bind(R.id.username)
    TextView usernameView;

    @Bind(R.id.user_type)
    TextView userTypeView;

    @Bind(R.id.kpi_benis)
    TextView benisView;

    @Bind(R.id.benis_delta)
    TextView benisDeltaView;

    @Bind(R.id.benis_container)
    View benisContainer;

    @Bind(R.id.benis_graph)
    ImageView benisGraph;

    @Bind(R.id.action_login)
    View loginView;

    @Bind(R.id.action_logout)
    View logoutView;

    @Bind(R.id.action_feedback)
    TextView feedbackView;

    @Bind(R.id.action_settings)
    View settingsView;

    @Bind(R.id.user_image)
    View userImageView;

    @Bind(R.id.drawer_nav_list)
    RecyclerView navItemsRecyclerView;

    private final NavigationAdapter navigationAdapter = new NavigationAdapter();

    private static final int ICON_ALPHA = 127;
    private final ColorStateList defaultColor = ColorStateList.valueOf(Color.BLACK);
    private ColorStateList markedColor;

    private final LoginActivity.DoIfAuthorizedHelper doIfAuthorizedHelper = LoginActivity.helper(this);


    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.left_drawer, container, false);
    }

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // get "marked" color
        int primary = ContextCompat.getColor(getActivity(), R.color.primary);
        markedColor = ColorStateList.valueOf(primary);

        // add some space on the top for the translucent status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams)
                    userImageView.getLayoutParams();

            params.topMargin += getStatusBarHeight(getActivity());
        }

        // initialize the top navigation items
        navItemsRecyclerView.setAdapter(navigationAdapter);
        navItemsRecyclerView.setLayoutManager(new WrapContentLinearLayoutManager(
                getActivity(), WrapContentLinearLayoutManager.VERTICAL, false));

        // add the static items to the navigation
        navigationAdapter.setNavigationItems(navigationProvider.categoryNavigationItems(
                Optional.<Info.User>absent(), false));

        settingsView.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), SettingsActivity.class);
            startActivity(intent);
        });

        loginView.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            startActivity(intent);
        });

        logoutView.setOnClickListener(v -> {
            LogoutDialogFragment fragment = new LogoutDialogFragment();
            fragment.show(getFragmentManager(), null);
        });

        benisGraph.setOnClickListener(this::onBenisGraphClicked);

        changeCompoundDrawableColor(feedbackView, defaultColor.withAlpha(ICON_ALPHA));

        feedbackView.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), FeedbackActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        doIfAuthorizedHelper.onActivityResult(requestCode, resultCode);
    }

    private void onBenisGraphClicked(View view) {
        DialogBuilder.start(getActivity())
                .content(R.string.benis_graph_explanation)
                .positive()
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();

        userService.loginState()
                .compose(bindToLifecycle())
                .subscribe(this::onLoginStateChanged, Actions.empty());

        navigationProvider.navigationItems()
                .compose(bindToLifecycle())
                .subscribe(
                        navigationAdapter::setNavigationItems,
                        ErrorDialogFragment.defaultOnError());

        benisGraph.setVisibility(settings.benisGraphEnabled() ? View.VISIBLE : View.GONE);
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
        if (state.isAuthorized()) {
            Info.User user = state.getInfo().getUser();
            View.OnClickListener onUsernameClicked = v -> getCallback().onUsernameClicked();

            usernameView.setText(user.getName());
            usernameView.setOnClickListener(onUsernameClicked);

            userTypeView.setVisibility(View.VISIBLE);
            userTypeView.setTextColor(ContextCompat.getColor(getContext(),
                    UserClasses.MarkColors.get(user.getMark())));

            userTypeView.setText(getString(UserClasses.MarkStrings.get(user.getMark())).toUpperCase());
            userTypeView.setOnClickListener(onUsernameClicked);

            Graph benis = state.getBenisHistory();

            benisView.setText(String.valueOf(user.getScore()));
            benisContainer.setVisibility(View.VISIBLE);
            benisGraph.setImageDrawable(new GraphDrawable(benis));

            if (benis.points().size() > 2)
                updateBenisDeltaForGraph(benis);

            loginView.setVisibility(View.GONE);
            logoutView.setVisibility(View.VISIBLE);
        } else {
            usernameView.setText(R.string.pr0gramm);
            usernameView.setOnClickListener(null);

            userTypeView.setText("");
            userTypeView.setVisibility(View.GONE);

            benisContainer.setVisibility(View.GONE);
            benisGraph.setImageDrawable(null);

            loginView.setVisibility(View.VISIBLE);
            logoutView.setVisibility(View.GONE);
        }
    }

    private void updateBenisDeltaForGraph(Graph benis) {
        int delta = (int) (benis.last().y - benis.first().y);
        if (delta == 0) {
            benisDeltaView.setVisibility(View.GONE);

        } else {
            benisDeltaView.setVisibility(View.VISIBLE);
            benisDeltaView.setTextColor(delta < 0
                    ? ContextCompat.getColor(getContext(), R.color.benis_delta_negative)
                    : ContextCompat.getColor(getContext(), R.color.benis_delta_positive));

            benisDeltaView.setText(String.format("%s%d", delta < 0 ? "↓" : "↑", delta));
        }
    }

    public void updateCurrentFilters(FeedFilter current) {
        navigationAdapter.setCurrentFilter(current);
    }

    public interface OnFeedFilterSelected {
        /**
         * Called if a drawer filter was clicked.
         *
         * @param filter The feed filter that was clicked.
         */
        void onFeedFilterSelectedInNavigation(FeedFilter filter);

        /**
         * Called if the user name itself was clicked.
         */
        void onUsernameClicked();

        /**
         * Some other menu item was clicked and we request that this
         * drawer gets closed
         */
        void onOtherNavigationItemClicked();

        /**
         * Navigate to the favorites of the given user
         * @param username
         */
        void onNavigateToFavorites(String username);
    }

    private OnFeedFilterSelected getCallback() {
        return (OnFeedFilterSelected) getActivity();
    }

    private class NavigationAdapter extends RecyclerView.Adapter<NavigationItemViewHolder> {
        private final List<NavigationItem> allItems = new ArrayList<>();
        private Optional<NavigationItem> selected = Optional.absent();
        private FeedFilter currentFilter;

        @Override
        public NavigationItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
            return new NavigationItemViewHolder(view);
        }

        @Override
        public int getItemViewType(int position) {
            return allItems.get(position).layout();
        }

        public void onBindViewHolder(NavigationItemViewHolder holder, int position) {
            NavigationItem item = allItems.get(position);
            holder.text.setText(item.title());

            // set the icon of the image
            holder.text.setCompoundDrawablesWithIntrinsicBounds(item.icon(), null, null, null);

            // update color
            ColorStateList color = (selected.orNull() == item) ? markedColor : defaultColor;
            holder.text.setTextColor(color);
            changeCompoundDrawableColor(holder.text, color.withAlpha(ICON_ALPHA));

            // handle clicks
            holder.itemView.setOnClickListener(v -> dispatchItemClick(item));

            holder.itemView.setOnLongClickListener(v -> {
                ifPresent(item.bookmark(), DrawerFragment.this::showDialogToRemoveBookmark);
                return true;
            });

            if (item.action() == NavigationProvider.ActionType.MESSAGES) {
                TextView unread = ButterKnife.findById(holder.itemView, unread_count);
                unread.setText(String.valueOf(item.unreadCount()));
                unread.setVisibility(item.unreadCount() > 0 ? View.VISIBLE : View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return allItems.size();
        }

        public void setNavigationItems(List<NavigationItem> items) {
            this.allItems.clear();
            this.allItems.addAll(items);
            merge();
        }

        public void setCurrentFilter(FeedFilter current) {
            currentFilter = current;
            merge();
        }

        private void merge() {
            // calculate the currently selected item
            selected = FluentIterable.from(allItems)
                    .filter(NavigationItem::hasFilter)
                    .filter(nav -> equal(currentFilter, nav.filter().orNull()))
                    .first();

            if (!selected.isPresent() && currentFilter != null) {
                selected = FluentIterable.from(allItems)
                        .filter(NavigationItem::hasFilter)
                        .filter(nav -> nav.filter().get().getFeedType() == currentFilter.getFeedType())
                        .first();
            }

            notifyDataSetChanged();
        }
    }

    private void dispatchItemClick(NavigationItem item) {
        switch (item.action()) {
            case FILTER:
            case BOOKMARK:
                getCallback().onFeedFilterSelectedInNavigation(item.filter().get());
                break;

            case UPLOAD:
                showUploadActivity();
                getCallback().onOtherNavigationItemClicked();
                break;

            case MESSAGES:
                showInboxActivity(item.unreadCount());
                getCallback().onOtherNavigationItemClicked();
                break;

            case FAVORITES:
                getCallback().onNavigateToFavorites(item.filter().get().getLikes().get());
                break;
        }
    }

    private void showInboxActivity(int unreadCount) {
        showInboxActivity(unreadCount == 0 ? InboxType.ALL : InboxType.UNREAD);
    }

    private void showInboxActivity(InboxType inboxType) {
        Runnable run = () -> {
            Intent intent = new Intent(getActivity(), InboxActivity.class);
            intent.putExtra(InboxActivity.EXTRA_INBOX_TYPE, inboxType.ordinal());
            startActivity(intent);
        };

        doIfAuthorizedHelper.run(run, run);
    }

    private void showUploadActivity() {
        Runnable run = () -> startActivity(new Intent(getActivity(), UploadActivity.class));
        doIfAuthorizedHelper.run(run, run);
    }

    private void showDialogToRemoveBookmark(Bookmark bookmark) {
        DialogBuilder.start(getActivity())
                .content(R.string.do_you_want_to_remove_this_bookmark)
                .positive(R.string.yes, () -> bookmarkService.delete(bookmark))
                .show();
    }

    private static class NavigationItemViewHolder extends RecyclerView.ViewHolder {
        final TextView text;

        public NavigationItemViewHolder(View itemView) {
            super(itemView);
            text = (TextView) (itemView instanceof TextView ?
                    itemView : itemView.findViewById(R.id.title));
        }
    }
}
