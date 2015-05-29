package com.pr0gramm.app.api.pr0gramm.response;

/**
 */
public class Posted {
    private String error;
    private Item item;

    public long getItemId() {
        return item.id;
    }

    public String getError() {
        return error;
    }

    private static class Item {
        long id;
    }
}
