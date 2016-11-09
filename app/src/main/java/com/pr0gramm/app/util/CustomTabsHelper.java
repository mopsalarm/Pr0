package com.pr0gramm.app.util;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsService;
import android.support.v4.content.ContextCompat;

import com.pr0gramm.app.services.ThemeHelper;
import com.thefinestartist.finestwebview.FinestWebView;

import java.util.ArrayList;
import java.util.List;

/**
 */

public class CustomTabsHelper {
    private static final String STABLE_PACKAGE = "com.android.chrome";
    private static final String BETA_PACKAGE = "com.chrome.beta";
    private static final String DEV_PACKAGE = "com.chrome.dev";
    private static final String LOCAL_PACKAGE = "com.google.android.apps.chrome";

    private final Activity activity;
    private String mPackageNameToUse;

    public CustomTabsHelper(Activity activity) {
        this.activity = activity;
    }

    private String getPackageName() {
        if (mPackageNameToUse != null) {
            return mPackageNameToUse;
        }

        // Get default VIEW intent handler that can view a web url.
        Intent activityIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.test-url.com"));

        // Get all apps that can handle VIEW intents.
        PackageManager pm = activity.getPackageManager();
        List<ResolveInfo> resolvedActivityList = pm.queryIntentActivities(activityIntent, 0);
        List<String> packagesSupportingCustomTabs = new ArrayList<>();
        for (ResolveInfo info : resolvedActivityList) {
            Intent serviceIntent = new Intent();
            serviceIntent.setAction(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION);
            serviceIntent.setPackage(info.activityInfo.packageName);
            if (pm.resolveService(serviceIntent, 0) != null) {
                packagesSupportingCustomTabs.add(info.activityInfo.packageName);
            }
        }

        // Now packagesSupportingCustomTabs contains all apps that can handle both VIEW intents
        // and service calls.
        if (packagesSupportingCustomTabs.isEmpty()) {
            mPackageNameToUse = null;
        } else if (packagesSupportingCustomTabs.size() == 1) {
            mPackageNameToUse = packagesSupportingCustomTabs.get(0);
        } else if (packagesSupportingCustomTabs.contains(STABLE_PACKAGE)) {
            mPackageNameToUse = STABLE_PACKAGE;
        } else if (packagesSupportingCustomTabs.contains(BETA_PACKAGE)) {
            mPackageNameToUse = BETA_PACKAGE;
        } else if (packagesSupportingCustomTabs.contains(DEV_PACKAGE)) {
            mPackageNameToUse = DEV_PACKAGE;
        } else if (packagesSupportingCustomTabs.contains(LOCAL_PACKAGE)) {
            mPackageNameToUse = LOCAL_PACKAGE;
        }
        return mPackageNameToUse;
    }

    public void openCustomTab(Uri uri) {
        int primaryColor = ContextCompat.getColor(activity, ThemeHelper.primaryColor());

        String packageName = getPackageName();
        if (packageName == null) {
            new FinestWebView.Builder(activity)
                    .theme(ThemeHelper.theme().noActionBar)
                    .iconDefaultColor(Color.WHITE)
                    .toolbarColorRes(ThemeHelper.theme().primaryColor)
                    .progressBarColorRes(ThemeHelper.theme().primaryColorDark)
                    .webViewSupportZoom(true)
                    .webViewBuiltInZoomControls(true)
                    .webViewDisplayZoomControls(false)
                    .show(uri.toString());

        } else {
            CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder()
                    .enableUrlBarHiding()
                    .addDefaultShareMenuItem()
                    .setToolbarColor(primaryColor)
                    .build();

            customTabsIntent.intent.setPackage(packageName);

            customTabsIntent.launchUrl(activity, uri);
        }
    }
}
