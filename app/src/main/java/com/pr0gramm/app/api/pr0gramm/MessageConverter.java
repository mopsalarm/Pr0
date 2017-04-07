package com.pr0gramm.app.api.pr0gramm;

import com.pr0gramm.app.feed.FeedItem;

/**
 */
public class MessageConverter {
    private MessageConverter() {
    }

    public static Api.Message of(Api.UserComments.UserInfo sender, Api.UserComments.UserComment comment) {
        return of(sender.getId(), sender.getName(), sender.getMark(), comment);
    }

    public static Api.Message of(Api.Info.User sender, Api.UserComments.UserComment comment) {
        return of(sender.getId(), sender.getName(), sender.getMark(), comment);
    }

    public static Api.Message of(Api.PrivateMessage message) {
        return ImmutableApi.Message.builder()
                .message(message.getMessage())
                .id(message.getId())
                .itemId(0)
                .creationTime(message.getCreated())
                .mark(message.getSenderMark())
                .name(message.getSenderName())
                .senderId(message.getSenderId())
                .score(0)
                .thumbnail(null)
                .build();
    }

    public static Api.Message of(FeedItem item, Api.Comment comment) {
        return ImmutableApi.Message.builder()
                .id((int) comment.getId())
                .itemId((int) item.id())
                .message(comment.getContent())
                .senderId(0)
                .name(comment.getName())
                .mark(comment.getMark())
                .score(comment.getUp() - comment.getDown())
                .creationTime(comment.getCreated())
                .thumbnail(item.thumbnail())
                .build();
    }

    public static Api.Message of(int senderId, String name, int mark, Api.UserComments.UserComment comment) {
        return ImmutableApi.Message.builder()
                .id((int) comment.getId())
                .creationTime(comment.getCreated())
                .score(comment.getUp() - comment.getDown())
                .itemId((int) comment.getItemId())
                .mark(mark)
                .name(name)
                .senderId(senderId)
                .message(comment.getContent())
                .thumbnail(comment.getThumb())
                .build();
    }
}
