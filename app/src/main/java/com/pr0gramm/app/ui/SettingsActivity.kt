package com.pr0gramm.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.fragment.app.transaction
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.pr0gramm.app.Instant
import com.pr0gramm.app.Settings
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.util.arguments


/**
 */
class SettingsActivity : BaseAppCompatActivity("SettingsActivity"), PreferenceFragmentCompat.OnPreferenceStartScreenCallback {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.basic)

        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val fragment = SettingsFragment().arguments {
                putString(
                        PreferenceFragmentCompat.ARG_PREFERENCE_ROOT,
                        intent?.extras?.getString("rootKey"))
            }

            supportFragmentManager.transaction {
                replace(android.R.id.content, fragment)
            }

            Settings.get().edit {
                putLong("_settings_last_seen", Instant.now().millis)
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

}
