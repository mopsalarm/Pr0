package com.pr0gramm.app.ui;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.response.Message;
import com.pr0gramm.app.api.pr0gramm.response.PrivateMessage;
import com.pr0gramm.app.ui.views.SenderInfoView;
import com.pr0gramm.app.ui.views.UsernameView;
import com.pr0gramm.app.util.AndroidUtility;

import java.util.List;

/**
 */
public class PrivateMessageAdapter extends RecyclerView.Adapter<PrivateMessageAdapter.MessageViewHolder> {
    private final List<MessageItem> messages;
    private final Context context;
    private final MessageActionListener actionListener;


    public PrivateMessageAdapter(Context context, List<PrivateMessage> messages,
                                 MessageActionListener actionListener) {

        this.context = context;
        this.actionListener = actionListener;
        this.messages = groupAndSort(messages);

        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return messages.get(position).message.getId();
    }

    @Override
    public MessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.inbox_private_message, parent, false);
        return new MessageViewHolder(view);
    }

    @SuppressWarnings("CodeBlock2Expr")
    @Override
    public void onBindViewHolder(MessageViewHolder view, int position) {
        MessageItem item = messages.get(position);

        // header for each "group"
        boolean firstOfGroup = position == 0 || messages.get(position - 1).partner.id != item.partner.id;
        view.header.setVisibility(firstOfGroup ? View.VISIBLE : View.GONE);
        view.senderName.setUsername(item.partner.name, item.partner.mark);

        // grey out our messages
        view.text.setTextColor(context.getResources().getColor(item.message.isSent()
                ? R.color.message_text_sent : R.color.message_text_received));

        // the text of the message
        AndroidUtility.linkify(view.text, item.message.getMessage());

        // sender info
        view.sender.setSingleLine(true);
        view.sender.setSenderName(item.message.getSenderName(), item.message.getSenderMark());
        view.sender.setPointsVisible(false);
        view.sender.setDate(item.message.getCreated());

        if (actionListener != null && !item.message.isSent()) {
            view.sender.setOnSenderClickedListener(v -> {
                actionListener.onUserClicked(item.message.getSenderId(), item.message.getSenderName());
            });

            view.sender.setOnAnswerClickedListener(v -> {
                actionListener.onAnswerToPrivateMessage(Message.of(item.message));
            });
        } else {
            // reset the answer click listener
            view.sender.setOnAnswerClickedListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        final TextView text;
        final TextView type;
        final SenderInfoView sender;
        final UsernameView senderName;
        final View header;
        final View divider;

        public MessageViewHolder(View itemView) {
            super(itemView);

            text = (TextView) itemView.findViewById(R.id.message_text);
            type = (TextView) itemView.findViewById(R.id.message_type);
            sender = (SenderInfoView) itemView.findViewById(R.id.sender_info);
            header = itemView.findViewById(R.id.header);
            senderName = (UsernameView) itemView.findViewById(R.id.sender_name);
            divider = itemView.findViewById(R.id.divider);
        }
    }

    private static class MessageItem {
        final PrivateMessage message;
        final PartnerKey partner;

        MessageItem(PrivateMessage message, PartnerKey partner) {
            this.message = message;
            this.partner = partner;
        }
    }

    private static class PartnerKey {
        final int id;
        final int mark;
        final String name;

        PartnerKey(int id, String name, int mark) {
            this.id = id;
            this.mark = mark;
            this.name = name;
        }

        @Override
        public int hashCode() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof PartnerKey && id == ((PartnerKey) o).id;
        }
    }

    private static ImmutableList<MessageItem> groupAndSort(List<PrivateMessage> messages) {
        List<MessageItem> enhanced = Lists.transform(messages, message -> {
            boolean outgoing = message.isSent();

            int partnerId = outgoing ? message.getRecipientId() : message.getSenderId();
            int partnerMark = outgoing ? message.getRecipientMark() : message.getRecipientMark();
            String partnerName = outgoing ? message.getRecipientName() : message.getSenderName();

            PartnerKey partnerKey = new PartnerKey(partnerId, partnerName, partnerMark);
            return new MessageItem(message, partnerKey);
        });

        return ImmutableList.copyOf(Multimaps.index(enhanced, item -> item.partner).values());
    }
}
