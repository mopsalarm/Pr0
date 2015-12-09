package com.pr0gramm.app.ui.fragments;

import android.content.Intent;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.api.pr0gramm.response.Message;
import com.pr0gramm.app.services.FavedCommentService;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.MessageAdapter;
import com.pr0gramm.app.ui.MessageView;

import java.util.List;

import javax.inject.Inject;

import static com.google.common.collect.Lists.transform;

/**
 */
public class FavedCommentFragment extends MessageInboxFragment {
    @Inject
    UserService userService;

    @Inject
    FavedCommentService favedCommentService;

    @Inject
    Settings settings;

    public FavedCommentFragment() {
        setHasOptionsMenu(true);
    }

    @Override
    protected LoaderHelper<List<Message>> newLoaderHelper() {
        return LoaderHelper.of(() -> {
            return favedCommentService
                    .list(settings.getContentType())
                    .map(comments -> transform(comments, FavedCommentService::commentToMessage));
        });
    }

    @Override
    protected MessageAdapter newMessageAdapter(List<Message> messages) {
        MessageAdapter adapter = super.newMessageAdapter(messages);
        adapter.setPointsVisibility(MessageView.PointsVisibility.NEVER);
        return adapter;
    }

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_kfav, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_info) {
            showKFavInfoPopup();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showKFavInfoPopup() {
        DialogBuilder.start(getContext())
                .content(R.string.info_kfav_userscript)
                .positive(R.string.open_website, di -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://goo.gl/py7xNW"));
                    getContext().startActivity(intent);
                })
                .negative(R.string.okay)
                .show();
    }
}
