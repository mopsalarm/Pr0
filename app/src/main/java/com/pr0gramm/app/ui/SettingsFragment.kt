package com.pr0gramm.app.ui

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.pr0gramm.app.*
import com.pr0gramm.app.services.*
import com.pr0gramm.app.services.preloading.PreloadManager
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.BasePreferenceFragment
import com.pr0gramm.app.ui.base.launchUntilPause
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment
import com.pr0gramm.app.ui.intro.IntroActivity
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.doInBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

class SettingsFragment : BasePreferenceFragment("SettingsFragment"),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val userService: UserService by instance()
    private val bookmarkService: BookmarkService by instance()
    private val preloadManager: PreloadManager by instance()
    private val recentSearchesServices: RecentSearchesServices by instance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!userService.isAuthorized) {
            // reset those content types - better be sure!
            Settings.resetContentTypeSettings()
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
                    logger.debug { "Removing preference ${pref.key}" }
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
        val preference: Preference = preferenceManager.findPreference("pref_pseudo_clean_preloaded")
            ?: return

        launchUntilPause {
            preloadManager.items.collect { items ->

                val totalSize = runInterruptible(Dispatchers.IO) {
                    items.values().sumOf { item ->
                        item.media.length().toInt() +
                                item.thumbnail.length().toInt() +
                                (item.thumbnailFull?.length()?.toInt() ?: 0)

                    }
                }

                preference.summary = getString(
                    R.string.pseudo_clean_preloaded_summary_with_size,
                    totalSize / (1024f * 1024f)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        preferenceScreen.sharedPreferences
            ?.registerOnSharedPreferenceChangeListener(this)

        updatePreloadInfo()
    }

    override fun onPause() {
        preferenceScreen.sharedPreferences
            ?.unregisterOnSharedPreferenceChangeListener(this)

        super.onPause()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
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
                val msg = context?.getString(R.string.pref_pseudo_clear_tag_suggestions_notification)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                return true
            }

            "pref_pseudo_onboarding" -> {
                IntroActivity.launch(requireActivity())
                return true
            }

            "pref_pseudo_restore_bookmarks" -> {
                launchWhenStarted(busyIndicator = true) {
                    bookmarkService.restore()
                }

                return true
            }

            "pref_pseudo_download_target" -> {
                val intent = Storage.openTreeIntent(requireContext())
                startActivityForResult(intent, RequestCodes.SELECT_DOWNLOAD_PATH)
                return true
            }

            else -> return super.onPreferenceTreeClick(preference)
        }
    }

    private fun showNoFileManagerAvailable() {
        showDialog(this) {
            content(R.string.hint_no_file_manager_available)
            positive(R.string.okay)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultIntent: Intent?) {
        if (requestCode == RequestCodes.SELECT_DOWNLOAD_PATH && resultCode == Activity.RESULT_OK) {
            if (!Storage.persistTreeUri(
                    requireContext(), resultIntent
                        ?: return
                )
            ) {
                showInvalidDownloadDirectorySelected()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, resultIntent)
        }
    }

    private fun showInvalidDownloadDirectorySelected() {
        showDialog(this) {
            content(R.string.error_invalid_download_directory)
            positive(R.string.okay)
        }
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences?, key: String?) {
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