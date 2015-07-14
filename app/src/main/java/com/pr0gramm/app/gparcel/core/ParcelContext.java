package com.pr0gramm.app.gparcel.core;

import com.google.gson.Gson;
import com.pr0gramm.app.Lazy;

/**
 */
class ParcelContext {
    private static final Lazy<ParcelContext> instance = new Lazy<ParcelContext>() {
        @Override
        protected ParcelContext compute() {
            return new ParcelContext();
        }
    };

    private final Gson gson;

    private ParcelContext() {
        gson = new Gson();
    }

    public static Gson gson() {
        return instance.get().gson;
    }

    static final byte NUMBER_LONG = 1;
    static final byte NUMBER_DOUBLE = 2;
    static final byte NUMBER_FLOAT = 3;
    static final byte NUMBER_INTEGER = 4;
    static final byte NUMBER_BYTE = 5;
}
