package com.pr0gramm.app.api.pr0gramm.response;

import java.util.List;

/**
 */
@SuppressWarnings("unused")
public class MessageFeed {
    private boolean hasNewer;
    private boolean hasOlder;
    private List<Message> messages;

    public boolean hasNewer() {
        return hasNewer;
    }

    public boolean hasOlder() {
        return hasOlder;
    }

    public List<Message> getMessages() {
        return messages;
    }
}
