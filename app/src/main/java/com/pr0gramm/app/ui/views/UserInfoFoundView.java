package com.pr0gramm.app.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.FrameLayout;

import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.response.Info;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 */
@SuppressLint("ViewConstructor")
public class UserInfoFoundView extends FrameLayout {
    private final UsernameView username;
    private final View uploads;
    private OnUserClickedListener uploadsClickedListener;

    public UserInfoFoundView(Context context, Info userInfo) {
        super(context);
        inflate(context, R.layout.user_uploads_link, this);

        username = findView(R.id.username);
        uploads = findView(R.id.uploads);

        set(userInfo);
    }

    public void set(Info info) {
        // user info
        Info.User user = info.getUser();
        username.setUsername(user.getName(), user.getMark());

        uploads.setOnClickListener(view -> {
            if (uploadsClickedListener != null) {
                uploadsClickedListener.onClicked(user.getId(), user.getName());
            }
        });
    }

    public void setUploadsClickedListener(OnUserClickedListener uploadsClickedListener) {
        this.uploadsClickedListener = uploadsClickedListener;
    }

    @NonNull
    @SuppressWarnings("unchecked")
    private <T extends View> T findView(@IdRes int id) {
        View view = checkNotNull(findViewById(id), "view not found");
        return (T) view;
    }

    public interface OnUserClickedListener {
        void onClicked(int userId, String name);
    }
}
