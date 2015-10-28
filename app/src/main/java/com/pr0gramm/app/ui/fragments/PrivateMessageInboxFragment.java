package com.pr0gramm.app.ui.fragments;

import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.response.Info;
import com.pr0gramm.app.api.pr0gramm.response.PrivateMessage;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.OptionMenuHelper;
import com.pr0gramm.app.ui.OptionMenuHelper.OnOptionsItemSelected;
import com.pr0gramm.app.ui.PrivateMessageAdapter;
import com.pr0gramm.app.ui.dialogs.SearchUserDialog;

import java.util.List;

import javax.inject.Inject;

/**
 */
public class PrivateMessageInboxFragment extends InboxFragment<PrivateMessage>
        implements SearchUserDialog.Listener {

    @Inject
    UserService userService;

    public PrivateMessageInboxFragment() {
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_private_messages, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return OptionMenuHelper.dispatch(this, item) || super.onOptionsItemSelected(item);
    }

    @OnOptionsItemSelected(R.id.action_new_message)
    public boolean showNewMessageDialog() {
        SearchUserDialog dialog = new SearchUserDialog();
        dialog.show(getChildFragmentManager(), null);
        return true;
    }

    @Override
    protected LoaderHelper<List<PrivateMessage>> newLoaderHelper() {
        return LoaderHelper.of(() -> getInboxService().getPrivateMessages());
    }

    @Override
    protected void displayMessages(RecyclerView recyclerView, List<PrivateMessage> messages) {
        recyclerView.setAdapter(new PrivateMessageAdapter(getActivity(), messages, actionListener));
    }

    @Override
    public void onUserInfo(Info info) {
        Info.User user = info.getUser();
        boolean isSelfInfo = userService.getName()
                .transform(user.getName()::equalsIgnoreCase)
                .or(false);

        if (!isSelfInfo) {
            // only allow sending to other people
            actionListener.onNewPrivateMessage(user.getId(), user.getName());
        }
    }

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }
}
