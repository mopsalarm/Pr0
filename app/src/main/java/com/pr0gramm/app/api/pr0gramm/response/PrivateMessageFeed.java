package com.pr0gramm.app.api.pr0gramm.response;

import java.util.List;

/**
 */
@SuppressWarnings("unused")
public class PrivateMessageFeed {
    private boolean hasNewer;
    private boolean hasOlder;
    List<PrivateMessage> messages;

    public boolean isHasNewer() {
        return hasNewer;
    }

    public boolean isHasOlder() {
        return hasOlder;
    }

    public List<PrivateMessage> getMessages() {
        return messages;
    }
}
