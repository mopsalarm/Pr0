package com.pr0gramm.app.ui.fragments;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.pr0gramm.app.api.pr0gramm.response.Message;
import com.pr0gramm.app.feed.ContentType;
import com.pr0gramm.app.services.UserService;

import java.util.EnumSet;
import java.util.List;

/**
 */
public class WrittenCommentFragment extends MessageInboxFragment {
    @Inject
    private UserService userService;

    @Override
    protected LoaderHelper<List<Message>> newLoaderHelper() {
        return LoaderHelper.of(() -> {
            String name = userService.getName().get();
            return getInboxService()
                    .getUserComments(name, EnumSet.allOf(ContentType.class))
                    .map(userComments -> {
                        //noinspection CodeBlock2Expr
                        return Lists.transform(userComments.getComments(),
                                comment -> Message.of(userComments.getUser(), comment));
                    });
        });
    }
}
