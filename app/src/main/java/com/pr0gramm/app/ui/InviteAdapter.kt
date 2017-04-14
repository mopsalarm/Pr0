package com.pr0gramm.app.ui

import android.content.Intent
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.ui.views.UsernameView
import com.pr0gramm.app.util.find
import net.danlew.android.joda.DateUtils.getRelativeTimeSpanString

/**
 */
class InviteAdapter(private val invites: List<Api.AccountInfo.Invite>) : RecyclerView.Adapter<InviteAdapter.InviteViewHolder>() {
    override fun getItemCount(): Int {
        return invites.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InviteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_invite, parent, false)
        return InviteViewHolder(view)
    }

    override fun onBindViewHolder(holder: InviteViewHolder, position: Int) {
        holder.update(invites[position])
    }

    inner class InviteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val email: TextView = find(R.id.email)
        val info: TextView = find(R.id.info)
        val username: UsernameView = find(R.id.username)

        fun update(invite: Api.AccountInfo.Invite) {
            val context = itemView.context

            val date = getRelativeTimeSpanString(context, invite.created())
            val oName = invite.name()
            if (oName.isPresent) {
                val name = oName.get()

                email.visibility = View.GONE
                username.visibility = View.VISIBLE
                username.setUsername(name, invite.mark().or(0))

                info.text = context.getString(R.string.invite_redeemed, invite.email(), date)
                itemView.setOnClickListener { openUsersProfile(name) }

            } else {
                username.visibility = View.GONE
                email.visibility = View.VISIBLE
                email.text = invite.email()

                info.text = context.getString(R.string.invite_unredeemed, date)
                itemView.setOnClickListener(null)
            }
        }

        private fun openUsersProfile(name: String) {
            val context = itemView.context
            val uriHelper = UriHelper.of(context)

            // open users profile
            val url = uriHelper.uploads(name)
            val intent = Intent(Intent.ACTION_VIEW, url, context, MainActivity::class.java)
            context.startActivity(intent)
        }
    }
}
