package com.pr0gramm.app.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.services.UriHelper;
import com.pr0gramm.app.ui.views.UsernameView;

import java.util.List;

import butterknife.ButterKnife;

import static net.danlew.android.joda.DateUtils.getRelativeTimeSpanString;

/**
 */
public class InviteAdapter extends RecyclerView.Adapter<InviteAdapter.InviteViewHolder> {
    private final ImmutableList<Api.AccountInfo.Invite> invites;

    public InviteAdapter(List<Api.AccountInfo.Invite> invites) {
        this.invites = ImmutableList.copyOf(invites);
    }

    @Override
    public int getItemCount() {
        return invites.size();
    }

    @Override
    public InviteViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_invite, parent, false);
        return new InviteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(InviteViewHolder holder, int position) {
        Api.AccountInfo.Invite invite = invites.get(position);
        holder.set(invite);
    }

    public class InviteViewHolder extends RecyclerView.ViewHolder {
        final TextView email;
        final TextView info;
        final UsernameView username;

        public InviteViewHolder(View itemView) {
            super(itemView);
            email = ButterKnife.findById(itemView, R.id.email);
            info = ButterKnife.findById(itemView, R.id.info);
            username = ButterKnife.findById(itemView, R.id.username);
        }

        public void set(Api.AccountInfo.Invite invite) {
            Context context = itemView.getContext();

            CharSequence date = getRelativeTimeSpanString(context, invite.created());
            Optional<String> oName = invite.name();
            if (oName.isPresent()) {
                String name = oName.get();

                email.setVisibility(View.GONE);
                username.setVisibility(View.VISIBLE);
                username.setUsername(name, invite.mark().or(0));

                info.setText(context.getString(R.string.invite_redeemed, invite.email(), date));
                itemView.setOnClickListener(v -> openUsersProfile(name));

            } else {
                username.setVisibility(View.GONE);
                email.setVisibility(View.VISIBLE);
                email.setText(invite.email());

                info.setText(context.getString(R.string.invite_unredeemed, date));
                itemView.setOnClickListener(null);
            }
        }

        private void openUsersProfile(String name) {
            Context context = itemView.getContext();
            UriHelper uriHelper = UriHelper.of(context);

            // open users profile
            Uri url = uriHelper.uploads(name);
            Intent intent = new Intent(Intent.ACTION_VIEW, url, context, MainActivity.class);
            context.startActivity(intent);
        }
    }
}
