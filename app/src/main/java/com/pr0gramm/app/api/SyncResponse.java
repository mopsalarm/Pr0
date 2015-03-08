package com.pr0gramm.app.api;

import com.google.common.primitives.Ints;

import java.util.List;

/**
 */
public class SyncResponse {
    private long lastId;
    private int[] log;

    public List<Integer> getLog() {
        return Ints.asList(log);
    }

    public long getLastId() {
        return lastId;
    }
}
