package com.pr0gramm.app.ui;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.response.Message;
import com.pr0gramm.app.ui.views.SenderInfoView;
import com.squareup.picasso.Picasso;

import java.util.List;

import roboguice.RoboGuice;

/**
 */
public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    private final List<Message> messages;
    private final Context context;
    private final Picasso picasso;

    public MessageAdapter(Context context, List<Message> messages) {
        this.context = context;
        this.messages = ImmutableList.copyOf(messages);
        this.picasso = RoboGuice.getInjector(context).getInstance(Picasso.class);

        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return messages.get(position).getId();
    }

    @Override
    public MessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.inbox_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(MessageViewHolder view, int position) {
        Message message = messages.get(position);

        // set the type. if we have no thumbnail, we have a private message
        boolean isComment = message.getThumb() != null;
        view.type.setText(isComment ? context.getString(R.string.inbox_message_comment) : context.getString(R.string.inbox_message_private));

        // the text of the message
        view.text.setText(message.getMessage());
        Linkify.addLinks(view.text, Linkify.WEB_URLS);

        // draw the image for this post
        if (isComment) {
            view.image.setVisibility(View.VISIBLE);

            String url = "http://thumb.pr0gramm.com/" + message.getThumb();
            picasso.load(url).into(view.image);
        } else {
            view.image.setVisibility(View.GONE);
            picasso.cancelRequest(view.image);
        }

        // sender info
        view.sender.setSenderName(message.getName(), message.getMark());
        view.sender.setPointsVisible(isComment);
        view.sender.setPoints(message.getScore());
        view.sender.setDate(message.getCreated());
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        final TextView text;
        final TextView type;
        final ImageView image;
        final SenderInfoView sender;

        public MessageViewHolder(View itemView) {
            super(itemView);

            text = (TextView) itemView.findViewById(R.id.message_text);
            type = (TextView) itemView.findViewById(R.id.message_type);
            image = (ImageView) itemView.findViewById(R.id.message_image);
            sender = (SenderInfoView) itemView.findViewById(R.id.sender_info);
        }
    }
}
