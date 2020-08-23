package com.pr0gramm.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.databinding.ActivityInviteBinding
import com.pr0gramm.app.services.InviteService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.bindViews
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.handleOnError
import com.pr0gramm.app.ui.views.SimpleAdapter
import com.pr0gramm.app.ui.views.UsernameView
import com.pr0gramm.app.ui.views.recyclerViewAdapter
import com.pr0gramm.app.util.DurationFormat
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.find
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

        find<View>(R.id.send_invite).setOnClickListener { onInviteClicked() }
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

    private fun inviteAdapter(invites: List<Api.AccountInfo.Invite>): SimpleAdapter<Api.AccountInfo.Invite> {
        return recyclerViewAdapter(invites) {
            handle<Api.AccountInfo.Invite>() with layout(R.layout.row_invite) { holder ->
                val email: TextView = holder.find(R.id.email)
                val info: TextView = holder.find(R.id.info)
                val username: UsernameView = holder.find(R.id.username)

                bind { invite ->
                    val context = itemView.context

                    val date = DurationFormat.timeToPointInTime(context, invite.created, short = false)
                    val name = invite.name
                    if (name != null) {
                        email.visibility = View.GONE
                        username.visibility = View.VISIBLE
                        username.setUsername(name, invite.mark ?: 0)

                        info.text = context.getString(R.string.invite_redeemed, invite.email, date)
                        itemView.setOnClickListener { openUserProfile(name) }

                    } else {
                        username.visibility = View.GONE
                        email.visibility = View.VISIBLE
                        email.text = invite.email

                        info.text = context.getString(R.string.invite_unredeemed, date)
                        itemView.setOnClickListener(null)
                    }
                }
            }
        }
    }

    private fun openUserProfile(name: String) {
        val url = UriHelper.of(this).uploads(name)
        startActivity(Intent(Intent.ACTION_VIEW, url, this, MainActivity::class.java))
    }
}
