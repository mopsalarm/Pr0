package com.pr0gramm.app.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.view.ViewGroup;

import com.google.common.base.Optional;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 */
public class TabsAdapter extends FragmentPagerAdapter {

    private final List<TabInfo> mTabs = new ArrayList<>();
    private final Map<Integer, WeakReference<Fragment>> mFragments = new HashMap<>();

    private final Context context;

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

    public TabsAdapter(FragmentActivity activity) {
        this(activity, activity.getSupportFragmentManager());
    }

    public TabsAdapter(Context context, FragmentManager fragmentManager) {
        super(fragmentManager);
        this.context = context;
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

    public void addTab(@StringRes int titleId, Class<?> cls, Bundle args) {
        String title = context.getString(titleId);
        TabInfo info = new TabInfo(title, cls, args);
        mTabs.add(info);
        notifyDataSetChanged();
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
}
