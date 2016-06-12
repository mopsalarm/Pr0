package com.pr0gramm.app.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.Api.Info;

import net.danlew.android.joda.DateUtils;

import org.joda.time.Duration;
import org.joda.time.Instant;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 */
@SuppressLint("ViewConstructor")
public class UserInfoCell extends FrameLayout {
    private final UsernameView username;
    private final TextView benis, favorites, comments, tags, uploads;
    private final View messages;
    private final View showComments;
    private final TextView extraInfo;
    private UserActionListener userActionListener;

    public UserInfoCell(Context context, Info userInfo) {
        super(context);
        inflate(context, R.layout.user_info_cell_v2, this);

        username = findView(R.id.username);
        benis = findView(R.id.kpi_benis);
        favorites = findView(R.id.kpi_favorites);
        uploads = findView(R.id.kpi_uploads);
        comments = findView(R.id.kpi_comments);
        tags = findView(R.id.kpi_tags);
        messages = findView(R.id.action_new_message);
        showComments = (View) findView(R.id.kpi_comments).getParent();
        extraInfo = findView(R.id.user_extra_info);

        set(userInfo);
    }

    public void set(Info info) {
        // user info
        Info.User user = info.getUser();
        username.setUsername(user.getName(), user.getMark());
        benis.setText(String.valueOf(user.getScore()));

        // counts
        tags.setText(String.valueOf(info.getTagCount()));
        comments.setText(String.valueOf(info.getCommentCount()));
        uploads.setText(String.valueOf(info.getUploadCount()));

        // open message dialog for user
        messages.setOnClickListener(view -> {
            if (userActionListener != null) {
                userActionListener.onWriteMessageClicked(user.getId(), user.getName());
            }
        });

        ((View) comments.getParent()).setOnClickListener(view -> {
            if (userActionListener != null) {
                userActionListener.onShowCommentsClicked();
            }
        });

        ((View) uploads.getParent()).setOnClickListener(view -> {
            if (userActionListener != null) {
                userActionListener.onShowUploadsClicked(user.getId(), user.getName());
            }
        });

        if (info.likesArePublic()) {
            favorites.setText(String.valueOf(info.getLikeCount()));

            ((View) favorites.getParent()).setOnClickListener(view -> {
                if (userActionListener != null) {
                    userActionListener.onUserFavoritesClicked(user.getName());
                }
            });
        } else {
            // remove the view
            ViewParent parent = favorites.getParent();
            ((View) parent).setVisibility(GONE);
        }

        // info about banned/register date
        if (user.isBanned() != 0) {
            Instant bannedUntil = user.getBannedUntil();
            if (bannedUntil == null) {
                extraInfo.setText(R.string.user_banned_forever);
            } else {
                Duration duration = new Duration(Instant.now(), bannedUntil);
                CharSequence durationStr = DateUtils.formatDuration(getContext(), duration);
                extraInfo.setText(getContext().getString(R.string.user_banned, durationStr));
            }
        } else {
            CharSequence registered = DateUtils.getRelativeTimeSpanString(getContext(),
                    user.getRegistered(), false);

            extraInfo.setText(getContext().getString(R.string.user_registered, registered));
        }
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

        void onShowUploadsClicked(int id, String name);
    }
}
