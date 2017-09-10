package com.pr0gramm.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.support.customtabs.CustomTabsIntent
import android.support.customtabs.CustomTabsService
import android.support.v4.content.ContextCompat
import com.pr0gramm.app.services.ThemeHelper
import com.thefinestartist.finestwebview.FinestWebView
import java.util.*

/**
 */

object BrowserHelper {
    private const val CHROME_STABLE_PACKAGE = "com.android.chrome"
    private const val CHROME_BETA_PACKAGE = "com.chrome.beta"
    private const val CHROME_DEV_PACKAGE = "com.chrome.dev"
    private const val CHROME_LOCAL_PACKAGE = "com.google.android.apps.chrome"

    private val chromeTabPackageName by memorize<Context, String?> { context ->
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

        return@memorize when {
            packagesSupportingCustomTabs.contains(CHROME_STABLE_PACKAGE) -> CHROME_STABLE_PACKAGE
            packagesSupportingCustomTabs.contains(CHROME_BETA_PACKAGE) -> CHROME_BETA_PACKAGE
            packagesSupportingCustomTabs.contains(CHROME_DEV_PACKAGE) -> CHROME_DEV_PACKAGE
            packagesSupportingCustomTabs.contains(CHROME_LOCAL_PACKAGE) -> CHROME_LOCAL_PACKAGE
            packagesSupportingCustomTabs.size == 1 -> packagesSupportingCustomTabs[0]
            else -> null
        }
    }

    private val firefoxFocusSupported by memorize<Context, Boolean> { context ->
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"))
        intent.`package` = "org.mozilla.focus"
        return@memorize context.packageManager.queryIntentActivities(intent, 0).size > 0
    }

    /**
     * Creates a builder with reasonable defaults to create a webview activity.
     */
    fun openIncognito(context: Context, url: String) {
        // if the firefox focus is installed, we'll open the page in this browser.
        if (firefoxFocusSupported(context)) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.`package` = "org.mozilla.focus"
            context.startActivity(intent)
            return
        }

        FinestWebView.Builder(context.applicationContext)
                .theme(ThemeHelper.theme.noActionBar)
                .iconDefaultColor(Color.WHITE)
                .toolbarColorRes(ThemeHelper.theme.primaryColor)
                .progressBarColorRes(ThemeHelper.theme.primaryColorDark)
                .webViewSupportZoom(true)
                .webViewBuiltInZoomControls(true)
                .webViewDisplayZoomControls(false)
                .show(url)
    }

    fun openCustomTab(context: Context, uri: Uri) {
        val themedContext = AndroidUtility.activityFromContext(context) ?: context

        val packageName = chromeTabPackageName(context)
        if (packageName == null) {
            openIncognito(context, uri.toString())

        } else {
            val customTabsIntent = CustomTabsIntent.Builder()
                    .enableUrlBarHiding()
                    .addDefaultShareMenuItem()
                    .setToolbarColor(ContextCompat.getColor(themedContext, ThemeHelper.theme.primaryColor))
                    .setSecondaryToolbarColor(ContextCompat.getColor(themedContext, ThemeHelper.theme.primaryColorDark))
                    .build()

            customTabsIntent.intent.`package` = packageName

            if (themedContext !is Activity) {
                customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            customTabsIntent.launchUrl(themedContext, uri)
        }
    }
}