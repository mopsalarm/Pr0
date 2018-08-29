package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.content.SharedPreferences
import android.os.Bundle
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.Instant.Companion.now
import com.pr0gramm.app.R
import com.pr0gramm.app.services.Update
import com.pr0gramm.app.services.UpdateChecker
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.ui.fragments.BusyDialog.Companion.busyDialog
import com.pr0gramm.app.util.Linkify
import com.pr0gramm.app.util.arguments
import com.pr0gramm.app.util.directKodein
import com.pr0gramm.app.util.trace
import com.trello.rxlifecycle.android.ActivityEvent
import org.kodein.di.erased.instance
import rx.Observable

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
        private fun newInstance(update: Update?): UpdateDialogFragment {
            return UpdateDialogFragment().arguments {
                putParcelable("update", update)
            }
        }

        /**
         * Check for updates and shows a new [UpdateDialogFragment]
         * if an update could be found.

         * @param activity The activity that starts this update check.
         */
        fun checkForUpdates(activity: BaseAppCompatActivity, interactive: Boolean) {
            trace { "checkForUpdates" }

            val shared = activity.directKodein.instance<SharedPreferences>()

            if (!interactive && !BuildConfig.DEBUG) {
                val last = Instant(shared.getLong(KEY_LAST_UPDATE_CHECK, 0))
                if (last.isAfter(Instant.now() - Duration.hours(1)))
                    return
            }

            // Action to store the last check time
            val storeCheckTime = {
                shared.edit()
                        .putLong(KEY_LAST_UPDATE_CHECK, now().millis)
                        .apply()
            }

            // show a busy-dialog or not?
            val busyOperator = if (interactive) busyDialog<Update?>(activity) else null

            // do the check
            UpdateChecker().check()
                    .onErrorResumeNext(Observable.empty())
                    .defaultIfEmpty(null)
                    .doAfterTerminate(storeCheckTime)
                    .compose(activity.bindUntilEventAsync<Update?>(ActivityEvent.STOP))
                    .run { busyOperator?.let { lift(it) } ?: this }
                    .subscribeWithErrorHandling(activity.supportFragmentManager) { update: Update? ->
                        if (interactive || update != null) {
                            trace { "showUpdateDialog" }
                            newInstance(update).show(activity.supportFragmentManager, null)
                        }
                    }
        }

        private const val KEY_LAST_UPDATE_CHECK = "UpdateDialogFragment.lastUpdateCheck"
    }
}
