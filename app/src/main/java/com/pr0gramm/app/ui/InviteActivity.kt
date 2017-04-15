package com.pr0gramm.app.ui

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.github.salomonbrys.kodein.instance
import com.google.common.base.Throwables
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.R
import com.pr0gramm.app.services.InviteService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.defaultOnError
import com.pr0gramm.app.util.Noop.noop
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.visible
import kotterknife.bindView
import kotterknife.bindViews
import rx.functions.Action1

/**
 */
class InviteActivity : BaseAppCompatActivity() {
    private val inviteService: InviteService by instance()

    private val mailField: EditText by bindView(R.id.mail)
    private val invites: RecyclerView by bindView(R.id.invites)
    private val remainingInvites: TextView by bindView(R.id.remaining)
    private val invitesEmptyHint: View by bindView(R.id.invites_empty)

    private val formFields: List<View> by bindViews(R.id.mail, R.id.send_invite)


    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.basic)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_invite)
        disableInputViews()

        invites.layoutManager = LinearLayoutManager(this)

        requeryInvites()

        find<View>(R.id.send_invite).setOnClickListener { onInviteClicked() }
    }

    override fun injectComponent(appComponent: ActivityComponent) {
    }

    fun onInviteClicked() {
        val email = mailField.text.toString()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            mailField.error = getString(R.string.error_email)
            return
        }

        // disable all views
        disableInputViews()

        inviteService.send(email)
                .compose(bindToLifecycleAsync<Any>().forCompletable())
                .doAfterTerminate { this.requeryInvites() }
                .subscribe({ this.onInviteSent() }, { this.onInviteError(it) })

        Track.inviteSent()
    }

    private fun requeryInvites() {
        inviteService.invites()
                .compose(bindToLifecycleAsync())
                .subscribe(Action1 { this.handleInvites(it) }, defaultOnError())
    }

    private fun handleInvites(invites: InviteService.Invites) {
        this.invites.adapter = InviteAdapter(invites.invited)
        this.invitesEmptyHint.visibility = if (invites.invited.isNotEmpty()) View.GONE else View.VISIBLE

        val text = getString(R.string.invite_remaining, invites.inviteCount)
        remainingInvites.text = text

        if (invites.inviteCount > 0) {
            enableInputViews()
        }
    }

    private fun enableInputViews() {
        formFields.forEach {
            it.visible = true
            it.isEnabled = true
        }
    }

    private fun disableInputViews() {
        formFields.forEach {
            it.isEnabled = false
        }
    }

    private fun onInviteSent() {
        Snackbar.make(mailField, R.string.invite_hint_success, Snackbar.LENGTH_SHORT)
                .setAction(R.string.okay, noop)
                .show()
    }

    private fun onInviteError(error: Throwable) {
        val cause = Throwables.getRootCause(error)
        if (cause is InviteService.InviteException) {
            when {
                cause.noMoreInvites() -> showDialog(this) {
                    content(R.string.invite_no_more_invites)
                    positive()
                }

                cause.emailFormat() -> showDialog(this) {
                    content(R.string.error_email)
                    positive()
                }

                cause.emailInUse() -> showDialog(this) {
                    content(R.string.invite_email_in_use)
                    positive()
                }

                else -> defaultOnError().call(error)
            }
        } else {
            defaultOnError().call(error)
        }
    }
}
