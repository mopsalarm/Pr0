package com.pr0gramm.app.ui

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.InviteService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.defaultOnError
import com.pr0gramm.app.ui.views.SimpleAdapter
import com.pr0gramm.app.ui.views.UsernameView
import com.pr0gramm.app.ui.views.recyclerViewAdapter
import com.pr0gramm.app.util.*
import kotterknife.bindView
import kotterknife.bindViews
import org.kodein.di.erased.instance
import rx.lang.kotlin.subscribeBy

/**
 */
class InviteActivity : BaseAppCompatActivity("InviteActivity") {
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

    fun onInviteClicked() {
        val email = mailField.text.toString()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            mailField.error = getString(R.string.error_email)
            return
        }

        // disable all views
        disableInputViews()

        inviteService.send(email)
                .decoupleSubscribe()
                .compose(bindToLifecycleAsync<Any>())
                .doAfterTerminate { this.requeryInvites() }
                .subscribeBy(onCompleted = { this.onInviteSent() }, onError = { this.onInviteError(it) })

        Track.inviteSent()
    }

    private fun requeryInvites() {
        inviteService.invites()
                .bindToLifecycleAsync()
                .subscribeWithErrorHandling { handleInvites(it) }
    }

    private fun handleInvites(invites: InviteService.Invites) {
        this.invites.adapter = inviteAdapter(invites.invited)
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

                else -> defaultOnError().call(error)
            }
        } else {
            defaultOnError().call(error)
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

                    val date = DurationFormat.timeToPointInTime(context, invite.created)
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
