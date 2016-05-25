package com.pr0gramm.app.ui.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.google.common.collect.FluentIterable;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.feed.FeedType;
import com.pr0gramm.app.services.UriHelper;
import com.pr0gramm.app.ui.MainActivity;
import com.pr0gramm.app.ui.MessageAdapter;

import java.util.Collections;
import java.util.List;

/**
 * Extends a normal {@link MessageAdapter} to display a users comment. If you click
 * one of those comments, it will open the post/comment.
 */
public class UserCommentsAdapter extends MessageAdapter {
    private final Activity activity;

    public UserCommentsAdapter(Activity activity) {
        super(activity, Collections.emptyList(), null, R.layout.user_info_comment);
        this.activity = activity;
    }

    @Override
    public void onBindViewHolder(MessageViewHolder view, int position) {
        super.onBindViewHolder(view, position);
        view.itemView.setOnClickListener(v -> {
            Api.Message message = this.messages.get(position);

            UriHelper uriHelper = UriHelper.of(activity);
            Uri uri = uriHelper.post(FeedType.NEW, message.getItemId(), message.id());
            Intent intent = new Intent(Intent.ACTION_VIEW, uri, activity, MainActivity.class);
            activity.startActivity(intent);
        });
    }

    public void setComments(Api.Info.User user, List<Api.UserComments.UserComment> comments) {
        setMessages(FluentIterable.from(comments)
                .transform(c -> Api.Message.of(user, c))
                .toList());
    }
}
