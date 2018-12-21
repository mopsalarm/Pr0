package com.pr0gramm.app.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.transaction
import androidx.preference.*
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.services.RecentSearchesServices
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.preloading.PreloadManager
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.BasePreferenceFragment
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment
import com.pr0gramm.app.ui.intro.IntroActivity
import com.pr0gramm.app.util.*
import org.kodein.di.KodeinAware
import org.kodein.di.erased.instance

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
            SharedPreferences.OnSharedPreferenceChangeListener, KodeinAware {

        private val settings = Settings.get()

        private val userService: UserService by instance()
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

            updateCodecPreference("pref_video_codec", "video/avc")
            updateCodecPreference("pref_audio_codec", "audio/mp4a-latm")

            if (!BuildConfig.DEBUG) {
                hidePreferenceByName("prefcat_debug")
            }

            if (!userService.userIsAdmin) {
                hidePreferenceByName("pref_show_content_type_flag")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // only allowed on older versions
                hidePreferenceByName("pref_use_exo_player")
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

        private fun updateCodecPreference(prefName: String, mimeType: String) {
            val pref = findPreference(prefName) as ListPreference? ?: return

            val entries = mutableListOf<CharSequence>()
            val entryValues = mutableListOf<CharSequence>()

            entries.add("Software")
            entryValues.add("software")

            entries.add("Hardware")
            entryValues.add("hardware")

            try {
                val codecs = MediaCodecUtil.getDecoderInfos(mimeType, false)
                for (codec in codecs) {
                    entries.add(codec.name.toLowerCase())
                    entryValues.add(codec.name)
                }
            } catch (ignored: MediaCodecUtil.DecoderQueryException) {
            }

            if (entries.size > 3) {
                pref.setDefaultValue("hardware")
                pref.entries = entries.toTypedArray()
                pref.entryValues = entryValues.toTypedArray()
            } else {
                pref.isEnabled = false
            }
        }

        private fun updatePreloadInfo() {
            val preference = preferenceManager.findPreference("pref_pseudo_clean_preloaded")
            if (preference != null) {
                preloadManager.all()
                        .subscribeOnBackground()
                        .map { items -> items.fold(0L) { sum, item -> sum + item.media.length() + item.thumbnail.length() } }
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
                    doInBackground { preloadManager.deleteBefore(Instant.now()) }
                    return true
                }

                "pref_pseudo_clear_tag_suggestions" -> {
                    recentSearchesServices.clearHistory()
                    return true
                }

                "pref_pseudo_onboarding" -> {
                    startActivity(Intent(activity, IntroActivity::class.java))
                    return true
                }

                else -> return super.onPreferenceTreeClick(preference)
            }
        }

        override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String) {
            // get the correct theme for the app!
            when (key) {
                "pref_convert_gif_to_webm" -> if (preferences.getBoolean(key, false)) {
                    showDialog(this) {
                        content(R.string.gif_as_webm_might_be_buggy)
                        positive()
                    }
                }

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
