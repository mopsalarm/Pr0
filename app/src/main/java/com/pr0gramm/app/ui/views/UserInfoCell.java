package com.pr0gramm.app.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.Info;

import net.danlew.android.joda.DateUtils;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 */
@SuppressLint("ViewConstructor")
public class UserInfoCell extends FrameLayout {
    private final UsernameView username;
    private final TextView benis, favorites, comments, tags, uploads, date;
    private final View messages;
    private final View showComments;
    private UserActionListener userActionListener;

    public UserInfoCell(Context context, Info userInfo) {
        super(context);
        inflate(context, R.layout.user_info_cell, this);

        username = findView(R.id.username);
        benis = findView(R.id.benis);
        favorites = findView(R.id.favorites);
        uploads = findView(R.id.uploads);
        tags = findView(R.id.tags);
        comments = findView(R.id.comments);
        date = findView(R.id.date);
        messages = findView(R.id.action_messages);
        showComments = findView(R.id.action_comments);

        set(userInfo);
    }

    public void set(Info info) {
        // user info
        Info.User user = info.getUser();
        username.setUsername(user.getName(), user.getMark());
        benis.setText(String.valueOf(user.getScore()));
        date.setText(DateUtils.formatDateTime(getContext(),
                user.getRegistered(), DateUtils.FORMAT_SHOW_DATE));

        // counts
        tags.setText(String.valueOf(info.getTagCount()));
        comments.setText(String.valueOf(info.getCommentCount()));
        uploads.setText(String.valueOf(info.getUploadCount()));
        favorites.setText(String.valueOf(info.getLikeCount()));

        // open message dialog for user
        messages.setOnClickListener(view -> {
            if (userActionListener != null) {
                userActionListener.onWriteMessageClicked(user.getId(), user.getName());
            }
        });

        favorites.setOnClickListener(view -> {
            if (userActionListener != null) {
                userActionListener.onUserFavoritesClicked(user.getName());
            }
        });

        showComments.setOnClickListener(view -> {
            if(userActionListener != null) {
                userActionListener.onShowCommentsClicked();
            }
        });
    }

    public void setUserActionListener(UserActionListener userActionListener) {
        this.userActionListener = userActionListener;
    }

    public void setWriteMessageEnabled(boolean enabled) {
        messages.setVisibility(enabled ? VISIBLE : GONE);
    }

    public void setShowCommentsEnabled(boolean enabled) {
        showComments.setVisibility(enabled ? VISIBLE : GONE);
    }

    @NonNull
    @SuppressWarnings("unchecked")
    private <T extends View> T findView(@IdRes int id) {
        View view = checkNotNull(findViewById(id), "view not found");
        return (T) view;
    }

    public interface UserActionListener {
        void onWriteMessageClicked(int userId, String name);

        void onUserFavoritesClicked(String name);

        void onShowCommentsClicked();
    }
}
