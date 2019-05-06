package com.pr0gramm.app.ui

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.transaction
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import com.llamalab.safs.FileSystems
import com.llamalab.safs.android.AndroidFileSystem
import com.pr0gramm.app.*
import com.pr0gramm.app.services.BookmarkService
import com.pr0gramm.app.services.RecentSearchesServices
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.preloading.PreloadManager
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.BasePreferenceFragment
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment
import com.pr0gramm.app.ui.intro.IntroActivity
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.arguments
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.observeOnMainThread
import rx.schedulers.Schedulers


/**
 */
class SettingsActivity : BaseAppCompatActivity("SettingsActivity"), PreferenceFragmentCompat.OnPreferenceStartScreenCallback {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.basic)
        super.onCreate(savedInstanceState)

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        if (savedInstanceState == null) {
            val fragment = SettingsFragment().arguments {
                putString(
                        PreferenceFragmentCompat.ARG_PREFERENCE_ROOT,
                        intent?.extras?.getString("rootKey"))
            }

            supportFragmentManager.transaction {
                replace(android.R.id.content, fragment)
            }
        }
    }

    override fun onPreferenceStartScreen(caller: PreferenceFragmentCompat?, pref: PreferenceScreen?): Boolean {
        pref ?: return false

        startActivity(Intent(this, SettingsActivity::class.java).apply {
            putExtra("rootKey", pref.key)
        })

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    class SettingsFragment : BasePreferenceFragment("SettingsFragment"),
            SharedPreferences.OnSharedPreferenceChangeListener {

        private val settings = Settings.get()

        private val userService: UserService by instance()
        private val bookmarkService: BookmarkService by instance()
        private val preloadManager: PreloadManager by instance()
        private val recentSearchesServices: RecentSearchesServices by instance()

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            if (!userService.isAuthorized) {
                // reset those content types - better be sure!
                settings.resetContentTypeSettings()
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            if (!BuildConfig.DEBUG) {
                hidePreferenceByName("prefcat_debug")
            }

            if (!bookmarkService.canEdit) {
                hidePreferenceByName("pref_pseudo_restore_bookmarks")
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                hidePreferenceByName("pref_pseudo_download_target")
            }

            tintPreferenceIcons(color = 0xffd0d0d0.toInt())
        }

        private fun hidePreferenceByName(name: String) {
            removeIf { it.key == name }
        }

        private fun removeIf(group: PreferenceGroup = preferenceScreen, predicate: (Preference) -> Boolean) {
            for (idx in (0 until group.preferenceCount).reversed()) {
                val pref = group.getPreference(idx)
                when {
                    predicate(pref) -> {
                        logger.info { "removing preference ${pref.key}" }
                        group.removePreference(pref)

                        // remove all preferences that have this pref as their dependency
                        removeIf { it.dependency == pref.key }
                    }

                    pref is PreferenceGroup -> removeIf(pref, predicate)
                }
            }
        }

        private fun tintPreferenceIcons(color: Int, group: PreferenceGroup = preferenceScreen) {
            for (idx in (0 until group.preferenceCount).reversed()) {
                val pref = group.getPreference(idx)
                if (pref is PreferenceGroup) {
                    tintPreferenceIcons(color, pref)
                }

                pref.icon?.let { icon ->
                    DrawableCompat.setTint(icon, color)
                }
            }
        }

        private fun updatePreloadInfo() {
            val preference = preferenceManager.findPreference("pref_pseudo_clean_preloaded")
            if (preference != null) {
                preloadManager.items
                        .subscribeOn(Schedulers.io())
                        .map { items ->
                            items.values().sumBy { (it.media.length() + it.thumbnail.length()).toInt() }
                        }
                        .observeOnMainThread()
                        .compose(bindToLifecycle())
                        .subscribe { totalSize ->
                            preference.summary = getString(
                                    R.string.pseudo_clean_preloaded_summary_with_size,
                                    totalSize / (1024f * 1024f))
                        }
            }
        }

        override fun onResume() {
            super.onResume()

            preferenceScreen.sharedPreferences
                    .registerOnSharedPreferenceChangeListener(this)

            updatePreloadInfo()
        }

        override fun onPause() {
            preferenceScreen.sharedPreferences
                    .unregisterOnSharedPreferenceChangeListener(this)

            super.onPause()
        }

        override fun onPreferenceTreeClick(preference: Preference?): Boolean {
            when (preference?.key) {
                "pref_pseudo_update" -> {
                    val activity = activity as BaseAppCompatActivity
                    UpdateDialogFragment.checkForUpdatesInteractive(activity)
                    return true
                }

                "pref_pseudo_changelog" -> {
                    val activity = activity as AppCompatActivity
                    ChangeLogDialog().show(activity.supportFragmentManager, null)
                    return true
                }

                "pref_pseudo_recommend" -> {
                    val text = "Probiere mal die offizielle pr0gramm App aus: https://app.pr0gramm.com/"
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.type = "text/plain"
                    intent.putExtra(Intent.EXTRA_SUBJECT, "pr0gramm app")
                    intent.putExtra(Intent.EXTRA_TEXT, text)
                    startActivity(Intent.createChooser(intent, getString(R.string.share_using)))
                    return true
                }

                "pref_pseudo_clean_preloaded" -> {
                    doInBackground { preloadManager.deleteOlderThan(Instant.now()) }
                    return true
                }

                "pref_pseudo_clear_tag_suggestions" -> {
                    recentSearchesServices.clearHistory()
                    return true
                }

                "pref_pseudo_onboarding" -> {
                    IntroActivity.launch(requireActivity())
                    return true
                }

                "pref_pseudo_restore_bookmarks" -> {
                    launchWithErrorHandler(busyIndicator = true) {
                        bookmarkService.restore()
                    }

                    return true
                }

                "pref_pseudo_download_target" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        startActivityForResult(intent, RequestCodes.SELECT_DOWNLOAD_PATH)
                    }

                    return true
                }

                else -> return super.onPreferenceTreeClick(preference)
            }
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, resultIntent: Intent?) {
            when (requestCode) {
                RequestCodes.SELECT_DOWNLOAD_PATH -> {
                    if (resultCode == Activity.RESULT_OK) {
                        val fs = FileSystems.getDefault() as AndroidFileSystem

                        val uri = resultIntent?.data ?: return
                        logger.warn { "Try to set $uri as new download directory" }

                        if (!AndroidFileSystem.isTreeUri(uri)) {
                            showInvalidDownloadDirectorySelected()
                            return
                        }

                        fs.takePersistableUriPermission(resultIntent)

                        try {
                            val path = fs.getPath(uri).toString()
                            Settings.get().edit { putString("pref_download_path", path) }
                        } catch (err: Exception) {
                            showInvalidDownloadDirectorySelected()
                            return
                        }
                    }
                }

                else -> super.onActivityResult(requestCode, resultCode, resultIntent)
            }
        }

        private fun showInvalidDownloadDirectorySelected() {
            showDialog(this) {
                content(R.string.error_invalid_download_directory)
                positive(R.string.okay)
            }
        }

        override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String) {
            // get the correct theme for the app!
            when (key) {
                "pref_theme" -> {
                    // get the correct theme for the app!
                    ThemeHelper.updateTheme()

                    // and apply to parent activity
                    activity?.let { AndroidUtility.recreateActivity(it) }
                }
            }
        }
    }
}
