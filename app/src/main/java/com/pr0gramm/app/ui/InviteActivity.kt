package com.pr0gramm.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.databinding.ActivityInviteBinding
import com.pr0gramm.app.databinding.RowInviteBinding
import com.pr0gramm.app.services.InviteService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.bindViews
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.handleOnError
import com.pr0gramm.app.ui.views.SingleTypeViewBindingAdapter
import com.pr0gramm.app.util.DurationFormat
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.rootCause
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 */
class InviteActivity : BaseAppCompatActivity("InviteActivity") {
    private val inviteService: InviteService by instance()

    private val views by bindViews(ActivityInviteBinding::inflate)

    private val formFields: List<View>
        get() = listOf(views.mail, views.sendInvite)


    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.basic)
        super.onCreate(savedInstanceState)

        setContentView(views)

        disableInputViews()

        views.invites.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        views.sendInvite.setOnClickListener { onInviteClicked() }
    }

    private fun onInviteClicked() {
        val email = views.mail.text.toString()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            views.mail.error = getString(R.string.error_email)
            return
        }

        // disable all views
        disableInputViews()

        launchWhenStarted(busyIndicator = true) {
            try {
                withContext(NonCancellable + Dispatchers.Default) {
                    inviteService.send(email)
                }

                onInviteSent()

                // re-query invites
                handleInvites(inviteService.invites())
            } catch (err: Throwable) {
                if (err !is CancellationException) {
                    onInviteError(err)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        launchWhenStarted {
            handleInvites(inviteService.invites())
        }
    }

    private fun handleInvites(invites: InviteService.Invites) {
        views.invites.adapter = inviteAdapter(invites.invited)
        views.invitesEmpty.visibility = if (invites.invited.isNotEmpty()) View.GONE else View.VISIBLE

        val text = getString(R.string.invite_remaining, invites.inviteCount)
        views.remaining.text = text

        if (invites.inviteCount > 0) {
            enableInputViews()
        }
    }

    private fun enableInputViews() {
        formFields.forEach {
            it.isVisible = true
            it.isEnabled = true
        }
    }

    private fun disableInputViews() {
        formFields.forEach {
            it.isEnabled = false
        }
    }

    private fun onInviteSent() {
        Track.inviteSent()

        Snackbar.make(views.mail, R.string.invite_hint_success, Snackbar.LENGTH_SHORT)
                .configureNewStyle()
                .setAction(R.string.okay, {})
                .show()
    }

    private fun onInviteError(error: Throwable) {
        val cause = error.rootCause
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

                else -> handleOnError(error)
            }
        } else {
            handleOnError(error)
        }
    }

    private fun inviteAdapter(invites: List<Api.AccountInfo.Invite>): SingleTypeViewBindingAdapter<Api.AccountInfo.Invite, RowInviteBinding> {
        return SingleTypeViewBindingAdapter(RowInviteBinding::inflate, invites) { (views), invite ->
            val context = views.root.context

            val date = DurationFormat.timeToPointInTime(context, invite.created, short = false)
            val name = invite.name
            if (name != null) {
                views.email.visibility = View.GONE
                views.username.visibility = View.VISIBLE
                views.username.setUsername(name, invite.mark ?: 0)

                views.info.text = context.getString(R.string.invite_redeemed, invite.email, date)
                views.root.setOnClickListener { openUserProfile(name) }

            } else {
                views.username.visibility = View.GONE
                views.email.visibility = View.VISIBLE
                views.email.text = invite.email

                views.info.text = context.getString(R.string.invite_unredeemed, date)
                views.root.setOnClickListener(null)
            }
        }
    }

    private fun openUserProfile(name: String) {
        val url = UriHelper.of(this).uploads(name)
        startActivity(Intent(Intent.ACTION_VIEW, url, this, MainActivity::class.java))
    }
}
