package com.pr0gramm.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
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
import com.pr0gramm.app.services.InboxService;
import com.pr0gramm.app.services.UserService;
import com.squareup.picasso.Picasso;

import java.util.List;

import javax.inject.Inject;

import roboguice.activity.RoboActionBarActivity;
import roboguice.inject.ContentView;
import roboguice.inject.InjectView;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static rx.android.observables.AndroidObservable.bindActivity;

/**
 * The activity that displays the inbox.
 */
@ContentView(R.layout.activity_inbox)
public class InboxActivity extends RoboActionBarActivity {
    @Inject
    private UserService userService;

    @Inject
    private InboxService inboxService;

    @Inject
    private Picasso picasso;

    @InjectView(R.id.messages)
    private RecyclerView messagesView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!userService.isAuthorized()) {
            openMainActivity();
            finish();
            return;
        }

        messagesView.setItemAnimator(null);
        messagesView.setLayoutManager(new LinearLayoutManager(this));

        bindActivity(this, inboxService.getInbox())
                .subscribe(this::onMessagesLoaded, defaultOnError());
    }

    private void onMessagesLoaded(List<Message> messages) {
        messagesView.setAdapter(new MessageAdapter(messages));
    }

    /**
     * Starts the main activity.
     */
    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    private class MessageAdapter extends RecyclerView.Adapter<MessageViewHolder> {
        private final List<Message> messages;

        public MessageAdapter(List<Message> messages) {
            this.messages = ImmutableList.copyOf(messages);
        }

        @Override
        public MessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(InboxActivity.this)
                    .inflate(R.layout.inbox_message, parent, false);

            return new MessageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(MessageViewHolder holder, int position) {
            Message message = messages.get(position);

            // set the type. if we have no thumbnail, we have a private message
            holder.type.setText(message.getThumb() != null
                    ? getString(R.string.inbox_message_comment)
                    : getString(R.string.inbox_message_private));

            // the text of the message
            holder.text.setText(message.getMessage());
            Linkify.addLinks(holder.text, Linkify.WEB_URLS);

            if(message.getThumb() != null) {
                holder.image.setVisibility(View.VISIBLE);

                String url = "http://thumb.pr0gramm.com/" + message.getThumb();
                picasso.load(url).into(holder.image);
            } else {
                holder.image.setVisibility(View.GONE);
                picasso.cancelRequest(holder.image);
            }
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }
    }

    private static class MessageViewHolder extends RecyclerView.ViewHolder {
        final TextView text;
        final TextView type;
        final ImageView image;

        public MessageViewHolder(View itemView) {
            super(itemView);

            text = (TextView) itemView.findViewById(R.id.message_text);
            type = (TextView) itemView.findViewById(R.id.message_type);
            image = (ImageView) itemView.findViewById(R.id.message_image);
        }
    }
}
