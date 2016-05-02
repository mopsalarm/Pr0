package com.pr0gramm.app.ui;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.response.AccountInfo;

import java.util.List;

import butterknife.ButterKnife;

import static net.danlew.android.joda.DateUtils.getRelativeTimeSpanString;

/**
 */
public class InviteAdapter extends RecyclerView.Adapter<InviteAdapter.InviteViewHolder> {
    private final ImmutableList<AccountInfo.Invite> invites;

    public InviteAdapter(List<AccountInfo.Invite> invites) {
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
        AccountInfo.Invite invite = invites.get(position);
        holder.set(invite);
    }

    public class InviteViewHolder extends RecyclerView.ViewHolder {
        final TextView email;
        final TextView info;

        public InviteViewHolder(View itemView) {
            super(itemView);
            email = ButterKnife.findById(itemView, R.id.email);
            info = ButterKnife.findById(itemView, R.id.info);
        }

        public void set(AccountInfo.Invite invite) {
            Context context = itemView.getContext();

            email.setText(invite.email());

            CharSequence date = getRelativeTimeSpanString(context, invite.created());
            if (invite.name().isPresent()) {
                info.setText(context.getString(R.string.invite_redeemed, invite.name().get(), date));
            } else {
                info.setText(context.getString(R.string.invite_unredeemed, date));
            }
        }
    }
}
