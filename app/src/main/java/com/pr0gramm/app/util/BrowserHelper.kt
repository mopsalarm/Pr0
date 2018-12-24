package com.pr0gramm.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.core.content.ContextCompat
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.fragments.withBusyDialog
import com.pr0gramm.app.ui.showDialog
import kotlinx.coroutines.launch
import org.kodein.di.erased.instance
import java.util.*

/**
 */

object BrowserHelper {
    private val logger = Logger("BrowserHelper")

    private const val CHROME_STABLE_PACKAGE = "com.android.chrome"
    private const val CHROME_BETA_PACKAGE = "com.chrome.beta"
    private const val CHROME_DEV_PACKAGE = "com.chrome.dev"
    private const val CHROME_LOCAL_PACKAGE = "com.google.android.apps.chrome"

    private val FIREFOX_URI = Uri.parse("https://play.google.com/store/apps/details?id=org.mozilla.klar&hl=en")

    private val chromeTabPackageName by memorize<Context, String?> { context ->
        try {
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
        } catch (err: Exception) {
            null
        }
    }

    private fun firefoxFocusPackage(context: Context): String? {
        return listOf("org.mozilla.klar", "org.mozilla.focus").firstOrNull { packageName ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"))
            intent.`package` = packageName
            context.packageManager.queryIntentActivities(intent, 0).size > 0
        }
    }

    fun open(context: Context, url: String) {
        if (context.directKodein.instance<Settings>().useIncognitoBrowser) {
            openIncognito(context, url)
        } else {
            openCustomTab(context, Uri.parse(url))
        }
    }

    /**
     * Try to open the url in a firefox focus window.
     * This one will never attempt to do session handover.
     */
    fun openIncognito(context: Context, url: String) {
        val uri = Uri.parse(url)

        // if the firefox focus is installed, we'll open the page in this browser.
        firefoxFocusPackage(context)?.let { packageName ->
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.`package` = packageName
            context.startActivity(intent)

            Track.openBrowser("FirefoxFocus")
            return
        }

        showDialog(context) {
            content(R.string.hint_use_firefox_focus)

            positive(R.string.play_store) {
                it.dismiss()

                openCustomTab(context, FIREFOX_URI)
                Track.gotoFirefoxFocusWebsite()
            }

            negative(R.string.cancel) {
                it.dismiss()
            }
        }
    }

    fun openCustomTab(context: Context, uri: Uri, handover: Boolean = true) {
        val themedContext = AndroidUtility.activityFromContext(context) ?: context

        fun open(block: (Uri) -> Unit) {
            if (handover) {
                runWithHandoverToken(context, uri) { uriWithHandoverToken ->
                    block(uriWithHandoverToken)
                }
            } else {
                block(uri)
            }

        }

        // get the chrome package to use
        val packageName = chromeTabPackageName(context)
        if (packageName == null) {
            Track.openBrowser("External")
            open { context.startActivity(Intent(Intent.ACTION_VIEW, it)) }
            return
        }

        val customTabsIntent = CustomTabsIntent.Builder()
                .enableUrlBarHiding()
                .addDefaultShareMenuItem()
                .setToolbarColor(ContextCompat.getColor(themedContext, ThemeHelper.theme.primaryColor))
                .setSecondaryToolbarColor(ContextCompat.getColor(themedContext, ThemeHelper.theme.primaryColorDark))
                .build()

        customTabsIntent.intent.`package` = packageName

        open { customTabsIntent.launchUrl(context, it) }
        Track.openBrowser("CustomTabs")
    }

    private fun runWithHandoverToken(context: Context, uri: Uri, block: (Uri) -> Unit) {
        // we need an activity to do the request and to show the 'busy dialog'
        val activity = AndroidUtility.activityFromContext(context)

        // we only want to do handover for pr0gramm urls
        val externalUri = uri.host?.toLowerCase() != "pr0gramm.com"

        // the user needs to be signed in for handover to make sense
        val userService = context.directKodein.instance<UserService>()
        val notAuthorized = !userService.isAuthorized

        if (activity !is BaseAppCompatActivity || externalUri || notAuthorized) {
            block(uri)
            return
        }

        val api = activity.directKodein.instance<Api>()

        activity.launch {
            block(try {
                val response = activity.withBusyDialog {
                    api.handoverToken(null).await()
                }

                Uri.parse("https://pr0gramm.com/api/user/handoverlogin").buildUpon()
                        .appendQueryParameter("path", uri.path)
                        .appendQueryParameter("token", response.token)
                        .build()

            } catch (err: Exception) {
                logger.warn(err) { "Error getting handover token" }
                return@launch
            })
        }
    }
}