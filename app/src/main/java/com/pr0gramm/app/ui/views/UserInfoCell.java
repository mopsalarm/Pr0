package com.pr0gramm.app.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.pr0gramm.app.AndroidUtility;
import com.pr0gramm.app.Graph;
import com.pr0gramm.app.GraphDrawable;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.response.Info;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 */
@SuppressLint("ViewConstructor")
public class UserInfoCell extends FrameLayout {
    private final UsernameView username;
    private final TextView benis, favorites, comments, tags, uploads;
    private final View messages;
    private final View showComments;
    private final View container;
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

        container = findView(R.id.user_cell_container);

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

        if(info.likesArePublic()) {
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
    }

    public void setBenisGraph(Graph graph) {
        int fillColor = getResources().getColor(R.color.public_benis_graph_background);
        int lineColor = getResources().getColor(R.color.public_benis_graph_stroke);

        GraphDrawable drawable = new GraphDrawable(graph);
        drawable.setFillColor(fillColor);
        drawable.setLineColor(lineColor);

        AndroidUtility.setViewBackground(container, drawable);
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
