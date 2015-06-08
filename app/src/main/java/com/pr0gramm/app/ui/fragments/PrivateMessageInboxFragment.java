package com.pr0gramm.app.ui.fragments;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.inject.Inject;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.Info;
import com.pr0gramm.app.api.pr0gramm.response.PrivateMessage;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.PrivateMessageAdapter;
import com.pr0gramm.app.ui.dialogs.SearchUserDialog;

import java.util.List;

import rx.Observable;

/**
 */
public class PrivateMessageInboxFragment extends InboxFragment<PrivateMessage>
        implements SearchUserDialog.Listener {

    @Inject
    private UserService userService;

    public PrivateMessageInboxFragment() {
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_private_messages, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_new_message) {
            SearchUserDialog dialog = new SearchUserDialog();
            dialog.show(getChildFragmentManager(), null);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected Observable<List<PrivateMessage>> newMessageObservable() {
        return getInboxService().getPrivateMessages();
    }

    @Override
    protected PrivateMessageAdapter newAdapter(List<PrivateMessage> messages) {
        return new PrivateMessageAdapter(getActivity(), messages, actionListener);
    }

    @Override
    public void onUserInfo(Info info) {
        Info.User user = info.getUser();
        boolean isSelfInfo = userService.getName()
                .transform(user.getName()::equalsIgnoreCase)
                .or(false);

        if(!isSelfInfo) {
            // only allow sending to other people
            actionListener.onAnswerToPrivateMessage(user.getId(), user.getName());
        }
    }
}
