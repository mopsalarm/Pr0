package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.app.DownloadManager
import android.content.SharedPreferences
import android.os.Bundle
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Dagger
import com.pr0gramm.app.R
import com.pr0gramm.app.services.Update
import com.pr0gramm.app.services.UpdateChecker
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.ui.fragments.BusyDialogFragment.busyDialog
import com.trello.rxlifecycle.android.ActivityEvent
import org.joda.time.Duration.standardHours
import org.joda.time.Instant
import org.joda.time.Instant.now
import rx.Observable
import javax.inject.Inject

/**
 */
class UpdateDialogFragment : BaseDialogFragment() {
    @Inject
    internal lateinit var downloadManager: DownloadManager

    @Inject
    internal lateinit var sharedPreferences: SharedPreferences

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val update = arguments.getParcelable<Update?>("update")
        return update?.let { updateAvailableDialog(it) } ?: noNewUpdateDialog()
    }

    private fun updateAvailableDialog(update: Update): Dialog {
        return dialog(activity) {
            content(getString(R.string.new_update_available, update.changelog()))
            positive(R.string.download) { UpdateChecker.download(activity, update) }
            negative(R.string.ignore)
        }
    }

    private fun noNewUpdateDialog(): Dialog {
        return dialog(activity) {
            content(R.string.no_new_update)
            positive()
        }
    }

    override fun injectComponent(activityComponent: ActivityComponent) {
        activityComponent.inject(this)
    }

    companion object {
        private fun newInstance(update: Update): UpdateDialogFragment {
            val bundle = Bundle()
            bundle.putParcelable("update", update)

            val dialog = UpdateDialogFragment()
            dialog.arguments = bundle
            return dialog
        }

        /**
         * Check for updates and shows a new [UpdateDialogFragment]
         * if an update could be found.

         * @param activity The activity that starts this update check.
         */
        fun checkForUpdates(activity: BaseAppCompatActivity, interactive: Boolean) {
            val shared = Dagger.appComponent(activity).sharedPreferences()

            if (!interactive && !BuildConfig.DEBUG) {
                val last = Instant(shared.getLong(KEY_LAST_UPDATE_CHECK, 0))
                if (last.isAfter(now().minus(standardHours(1))))
                    return
            }

            // Action to store the last check time
            val storeCheckTime = {
                shared.edit()
                        .putLong(KEY_LAST_UPDATE_CHECK, now().millis)
                        .apply()
            }

            // show a busy-dialog or not?
            val busyOperator = if (interactive) busyDialog<Update>(activity) else null

            // do the check
            UpdateChecker(activity).check()
                    .onErrorResumeNext(Observable.empty<Update>())
                    .defaultIfEmpty(null)
                    .compose(activity.bindUntilEventAsync<Update>(ActivityEvent.DESTROY))
                    .run { busyOperator?.let { lift(it) } ?: this }
                    .doAfterTerminate(storeCheckTime)
                    .subscribe({ update ->
                        if (interactive || update != null) {
                            val dialog = newInstance(update)
                            dialog.show(activity.supportFragmentManager, null)
                        }
                    }, {})
        }

        private val KEY_LAST_UPDATE_CHECK = "UpdateDialogFragment.lastUpdateCheck"
    }
}
