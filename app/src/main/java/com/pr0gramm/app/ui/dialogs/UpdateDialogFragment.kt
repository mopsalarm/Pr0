package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.core.content.edit
import androidx.fragment.app.FragmentManager
import com.pr0gramm.app.*
import com.pr0gramm.app.services.SingleShotService
import com.pr0gramm.app.services.Update
import com.pr0gramm.app.services.UpdateChecker
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.util.Linkify
import com.pr0gramm.app.util.arguments
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.trace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 */
class UpdateDialogFragment : BaseDialogFragment("UpdateDialogFragment") {
    private val singleShotService: SingleShotService by instance()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val update = arguments?.getParcelable<Update?>("update")
        return update?.let { updateAvailableDialog(it) } ?: noNewUpdateDialog()
    }

    private fun updateAvailableDialog(update: Update): Dialog {
        var content = getString(R.string.new_update_available, update.changelog)

        return dialog(this) {
            positive(R.string.install_update) {
                singleShotService.markAsDoneOnce("update:${update.version}")
                UpdateChecker.download(requireActivity(), update)
            }

            if (!singleShotService.isFirstTime("update:${update.version}")) {
                neutral(R.string.download_manually) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.apk)))
                }

                content += "\n\n" + getString(R.string.new_update_download)
            }

            content(Linkify.linkify(requireContext(), content))
        }
    }

    private fun noNewUpdateDialog(): Dialog {
        return dialog(this) {
            content(R.string.no_new_update)
            positive()
        }
    }

    companion object {
        private val logger = Logger("Update")

        private fun newInstance(update: Update?): UpdateDialogFragment {
            return UpdateDialogFragment().arguments {
                putParcelable("update", update)
            }
        }

        suspend fun checkForUpdatesInBackground(context: Context, fm: FragmentManager) {
            val prefs = context.injector.instance<SharedPreferences>()

            // only check once an hour.
            if (!BuildConfig.DEBUG) {
                val last = Instant(prefs.getLong(KEY_LAST_UPDATE_CHECK, 0))
                if (last.isAfter(Instant.now() - Duration.hours(1)))
                    return
            }

            val update = withContext(Dispatchers.Default) {
                UpdateChecker().queryAll()
            }

            // remember that we've checked
            prefs.edit {
                putLong(KEY_LAST_UPDATE_CHECK, Instant.now().millis)
            }

            // ignore errors
            if (update is UpdateChecker.Response.Error) {
                logger.warn(update.err) { "Ignoring error during update" }
                return
            }

            if (coroutineContext[Job]?.isCompleted == true) {
                logger.warn { "coroutineContext.job is completed, but still in coroutine?" }
                return
            }

            if (update is UpdateChecker.Response.UpdateAvailable) {
                if (!fm.isStateSaved) {
                    newInstance(update.update).show(fm, null)
                }
            }
        }

        fun checkForUpdatesInteractive(activity: BaseAppCompatActivity) {
            trace { "checkForUpdates" }

            val prefs = activity.applicationContext.injector.instance<SharedPreferences>()

            activity.launchWhenStarted(busyIndicator = true) {
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
