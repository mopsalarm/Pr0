package com.pr0gramm.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.support.customtabs.CustomTabsIntent
import android.support.customtabs.CustomTabsService
import android.support.v4.content.ContextCompat.getColor
import com.pr0gramm.app.services.ThemeHelper
import com.thefinestartist.finestwebview.FinestWebView
import java.util.*

/**
 */

class CustomTabsHelper(context: Context) {

    private val context = AndroidUtility.activityFromContext(context).orNull() ?: context

    private val packageName: String? get() {
        if (sPackageNameToUse != null) {
            return sPackageNameToUse
        }

        val activityIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"))

        val pm = context.packageManager
        val packagesSupportingCustomTabs = ArrayList<String>()
        for (info in pm.queryIntentActivities(activityIntent, 0)) {
            val serviceIntent = Intent()
            serviceIntent.action = CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION
            serviceIntent.`package` = info.activityInfo.packageName
            if (pm.resolveService(serviceIntent, 0) != null) {
                packagesSupportingCustomTabs.add(info.activityInfo.packageName)
            }
        }

        sPackageNameToUse = when {
            packagesSupportingCustomTabs.contains(STABLE_PACKAGE) -> STABLE_PACKAGE
            packagesSupportingCustomTabs.contains(BETA_PACKAGE) -> BETA_PACKAGE
            packagesSupportingCustomTabs.contains(DEV_PACKAGE) -> DEV_PACKAGE
            packagesSupportingCustomTabs.contains(LOCAL_PACKAGE) -> LOCAL_PACKAGE
            packagesSupportingCustomTabs.size == 1 -> packagesSupportingCustomTabs[0]
            else -> null
        }

        return sPackageNameToUse
    }

    fun openCustomTab(uri: Uri) {
        if (packageName == null) {
            newWebviewBuilder(context).show(uri.toString())

        } else {
            val customTabsIntent = CustomTabsIntent.Builder()
                    .enableUrlBarHiding()
                    .addDefaultShareMenuItem()
                    .setToolbarColor(getColor(context, ThemeHelper.theme.primaryColor))
                    .setSecondaryToolbarColor(getColor(context, ThemeHelper.theme.primaryColorDark))
                    .build()

            customTabsIntent.intent.`package` = packageName

            if (context !is Activity) {
                customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            customTabsIntent.launchUrl(context, uri)
        }
    }

    companion object {
        private val STABLE_PACKAGE = "com.android.chrome"
        private val BETA_PACKAGE = "com.chrome.beta"
        private val DEV_PACKAGE = "com.chrome.dev"
        private val LOCAL_PACKAGE = "com.google.android.apps.chrome"

        private var sPackageNameToUse: String? = null

        /**
         * Creates a builder with reasonable defaults to create a webview activity.
         */
        @JvmStatic
        fun newWebviewBuilder(context: Context): FinestWebView.Builder {
            return FinestWebView.Builder(context.applicationContext)
                    .theme(ThemeHelper.theme.noActionBar)
                    .iconDefaultColor(Color.WHITE)
                    .toolbarColorRes(ThemeHelper.theme.primaryColor)
                    .progressBarColorRes(ThemeHelper.theme.primaryColorDark)
                    .webViewSupportZoom(true)
                    .webViewBuiltInZoomControls(true)
                    .webViewDisplayZoomControls(false)
        }
    }
}
