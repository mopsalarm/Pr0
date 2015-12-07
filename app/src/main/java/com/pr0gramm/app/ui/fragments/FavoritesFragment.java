package com.pr0gramm.app.ui.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.google.common.base.Optional;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.feed.FeedType;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.FilterFragment;
import com.pr0gramm.app.ui.ScrollHideToolbarListener;
import com.pr0gramm.app.ui.TabsAdapter;
import com.pr0gramm.app.ui.base.BaseFragment;
import com.pr0gramm.app.util.AndroidUtility;

import javax.inject.Inject;

import butterknife.Bind;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 */
public class FavoritesFragment extends BaseFragment implements FilterFragment {
    private static final String ARG_USERNAME = "FavoritesFragment.username";

    @Bind(R.id.favorites_pager)
    ViewPager pager;

    @Bind(R.id.tabs)
    TabLayout tabLayout;

    @Inject
    UserService userService;

    private FeedFilter feedFilter;

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // build the filter for this view
        String username = getArguments().getString(ARG_USERNAME, "");
        feedFilter = new FeedFilter().withFeedType(FeedType.NEW).withLikes(username);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorites, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        fixViewTopOffset(view);
        resetToolbar();

        FeedFilter filter = new FeedFilter().withFeedType(FeedType.NEW).withLikes("Mopsalarm");
        Bundle feedFragmentArguments = FeedFragment.newArguments(filter, false, Optional.absent());

        TabsAdapter adapter = new TabsAdapter(getContext(), getChildFragmentManager());
        adapter.addTab(R.string.action_favorites, FeedFragment.class, feedFragmentArguments);
        adapter.addTab(R.string.action_kfav, FavedCommentFragment.class, null);
        pager.setAdapter(adapter);

        tabLayout.setupWithViewPager(pager);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return true;
    }

    private void resetToolbar() {
        FragmentActivity activity = getActivity();
        if (activity instanceof ScrollHideToolbarListener.ToolbarActivity) {
            ScrollHideToolbarListener listener = ((ScrollHideToolbarListener.ToolbarActivity) activity)
                    .getScrollHideToolbarListener();

            listener.reset();
        }
    }

    private void fixViewTopOffset(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;
            marginParams.topMargin = AndroidUtility.getActionBarContentOffset(getContext());
            view.setLayoutParams(params);
        }
    }

    public static FavoritesFragment newInstance(String username) {
        checkNotNull(username, "Username must not be null");

        Bundle args = new Bundle();
        args.putString(ARG_USERNAME, username);

        FavoritesFragment fragment = new FavoritesFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public FeedFilter getCurrentFilter() {
        return feedFilter;
    }
}
