package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.edit
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.services.Update
import com.pr0gramm.app.services.UpdateChecker
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.util.*
import org.kodein.di.erased.instance

/**
 */
class UpdateDialogFragment : BaseDialogFragment("UpdateDialogFragment") {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val update = arguments?.getParcelable<Update?>("update")
        return update?.let { updateAvailableDialog(it) } ?: noNewUpdateDialog()
    }

    private fun updateAvailableDialog(update: Update): Dialog {
        val content = getString(R.string.new_update_available, update.changelog)

        return dialog(this) {
            content(Linkify.linkify(requireContext(), content))
            positive(R.string.install_update) { activity?.let { UpdateChecker.download(it, update) } }
        }
    }

    private fun noNewUpdateDialog(): Dialog {
        return dialog(this) {
            content(R.string.no_new_update)
            positive()
        }
    }

    companion object {
        private val logger = logger("Update")

        private fun newInstance(update: Update?): UpdateDialogFragment {
            return UpdateDialogFragment().arguments {
                putParcelable("update", update)
            }
        }

        fun checkForUpdatesInBackground(activity: BaseAppCompatActivity) {
            val prefs = activity.directKodein.instance<SharedPreferences>()

            // only check once an hour.
            if (!BuildConfig.DEBUG) {
                val last = Instant(prefs.getLong(KEY_LAST_UPDATE_CHECK, 0))
                if (last.isAfter(Instant.now() - Duration.hours(1)))
                    return
            }

            activity.launchWithErrorHandler {
                val update = UpdateChecker().queryAll()

                // remember that we've checked
                prefs.edit {
                    putLong(KEY_LAST_UPDATE_CHECK, Instant.now().millis)
                }

                // ignore errors
                if (update is UpdateChecker.Response.Error) {
                    logger.warn(update.err) { "Ignoring error during update" }
                    return@launchWithErrorHandler
                }

                if (update is UpdateChecker.Response.UpdateAvailable) {
                    newInstance(update.update).show(activity.supportFragmentManager, null)
                }
            }
        }

        fun checkForUpdatesInteractive(activity: BaseAppCompatActivity) {
            trace { "checkForUpdates" }

            val prefs = activity.directKodein.instance<SharedPreferences>()

            activity.launchWithErrorHandler(busyDialog = true) {
                val update = UpdateChecker().queryAll()

                // remember that we've checked
                prefs.edit {
                    putLong(KEY_LAST_UPDATE_CHECK, Instant.now().millis)
                }

                // let the activity handle the error
                if (update is UpdateChecker.Response.Error) {
                    throw update.err
                }

                val up = (update as? UpdateChecker.Response.UpdateAvailable)?.update
                newInstance(up).show(activity.supportFragmentManager, null)
            }
        }

        private const val KEY_LAST_UPDATE_CHECK = "UpdateDialogFragment.lastUpdateCheck"
    }
}
