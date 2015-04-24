package com.pr0gramm.app.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.pr0gramm.app.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapted from https://github.com/android/platform_development/blob/2d52182dfac91014c2975a1bb1afd99a3b14b4e9/samples/Support4Demos/src/com/example/android/supportv4/app/FragmentTabsPager.java
 * <p>
 * This is a helper class that implements the management of tabs and all
 * details of connecting a ViewPager with associated TabHost.  It relies on a
 * trick.  Normally a tab host has a simple API for supplying a View or
 * Intent that each tab will show.  This is not sufficient for switching
 * between pages.  So instead we make the content part of the tab host
 * 0dp high (it is not shown) and the TabsAdapter supplies its own dummy
 * view to show as the tab content.  It listens to changes in tabs, and takes
 * care of switch to the correct paged in the ViewPager whenever the selected
 * tab changes.
 */
public class TabsAdapter extends FragmentPagerAdapter
        implements TabHost.OnTabChangeListener, ViewPager.OnPageChangeListener {

    private final List<TabInfo> mTabs = new ArrayList<>();
    private final Map<Integer, WeakReference<Fragment>> mFragments = new HashMap<>();

    private final Context context;
    private final TabHost tabHost;
    private final TabWidget tabWidget;
    private final ViewPager viewPager;

    private TabHost.OnTabChangeListener onTabChangeListener;

    private static final class TabInfo {
        final String title;
        final Class<?> cls;
        final Bundle args;

        TabInfo(String title, Class<?> cls, Bundle args) {
            this.title = title;
            this.cls = cls;
            this.args = args;
        }
    }

    private static final class DummyTabFactory implements TabHost.TabContentFactory {
        private final Context mContext;

        public DummyTabFactory(Context context) {
            mContext = context;
        }

        @Override
        public View createTabContent(String tag) {
            View v = new View(mContext);
            v.setMinimumWidth(0);
            v.setMinimumHeight(0);
            return v;
        }
    }

    public TabsAdapter(FragmentActivity activity, TabHost tabHost, TabWidget tabWidget, ViewPager viewPager) {
        super(activity.getSupportFragmentManager());
        this.context = activity;
        this.tabHost = tabHost;
        this.tabWidget = tabWidget;
        this.viewPager = viewPager;
        this.tabHost.setOnTabChangedListener(this);

        this.viewPager.setAdapter(this);
        this.viewPager.setOnPageChangeListener(this);
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        Fragment fragment = (Fragment) super.instantiateItem(container, position);
        mFragments.put(position, new WeakReference<>(fragment));
        return fragment;
    }

    public Optional<Fragment> getTabFragment(int position) {
        WeakReference<Fragment> result = mFragments.get(position);
        return Optional.fromNullable(result != null ? result.get() : null);
    }

    public void addTab(TabHost.TabSpec tabSpec, @StringRes int titleId, Class<?> cls, Bundle args) {
        tabSpec.setContent(new DummyTabFactory(context));

        String title = context.getString(titleId);
        TabInfo info = new TabInfo(title, cls, args);
        mTabs.add(info);
        tabHost.addTab(tabSpec.setIndicator(createIndicator(title)));
        notifyDataSetChanged();
    }

    protected View createIndicator(String title) {
        TextView view = (TextView) LayoutInflater.from(context)
                .inflate(R.layout.tab_widget_title, tabWidget, false);

        view.setText(title.toUpperCase());
        return view;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return mTabs.get(position).title;
    }

    @Override
    public int getCount() {
        return mTabs.size();
    }

    @Override
    public Fragment getItem(int position) {
        TabInfo info = mTabs.get(position);
        return Fragment.instantiate(context, info.cls.getName(), info.args);
    }

    @Override
    public void onTabChanged(String tabId) {
        int position = tabHost.getCurrentTab();
        viewPager.setCurrentItem(position);

        if (onTabChangeListener != null)
            onTabChangeListener.onTabChanged(tabId);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
        // Unfortunately when TabHost changes the current tab, it kindly
        // also takes care of putting focus on it when not in touch mode.
        // The jerk.
        // This hack tries to prevent this from pulling focus out of our
        // ViewPager.
        TabWidget widget = tabHost.getTabWidget();
        int oldFocusability = widget.getDescendantFocusability();
        widget.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        tabHost.setCurrentTab(position);
        widget.setDescendantFocusability(oldFocusability);
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    public void setOnTabChangedListener(TabHost.OnTabChangeListener onTabChangeListener) {
        this.onTabChangeListener = onTabChangeListener;
    }
}
