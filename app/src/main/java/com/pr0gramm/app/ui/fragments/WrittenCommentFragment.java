package com.pr0gramm.app.ui.fragments;

import com.google.common.collect.Lists;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.feed.ContentType;
import com.pr0gramm.app.services.UserService;

import java.util.EnumSet;
import java.util.List;

import javax.inject.Inject;

import rx.Observable;

/**
 */
public class WrittenCommentFragment extends MessageInboxFragment {
    @Inject
    UserService userService;

    @Override
    protected LoaderHelper<List<Api.Message>> newLoaderHelper() {
        return LoaderHelper.of(() -> {
            if (!userService.getName().isPresent())
                return Observable.empty();

            String name = userService.getName().get();
            return getInboxService()
                    .getUserComments(name, EnumSet.allOf(ContentType.class))
                    .map(userComments -> {
                        //noinspection CodeBlock2Expr
                        return Lists.transform(userComments.getComments(),
                                comment -> Api.Message.of(userComments.getUser(), comment));
                    });
        });
    }

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }
}
