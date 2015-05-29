package com.pr0gramm.app.api.pr0gramm.response;

import com.google.common.primitives.Ints;

import java.util.List;

/**
 */
@SuppressWarnings("UnusedDeclaration")
public class Sync {
    private long lastId;
    private int inboxCount;
    private int[] log;

    public List<Integer> getLog() {
        return Ints.asList(log);
    }

    public long getLastId() {
        return lastId;
    }

    public int getInboxCount() {
        return inboxCount;
    }
}
