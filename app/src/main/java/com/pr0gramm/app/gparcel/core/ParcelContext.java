package com.pr0gramm.app.gparcel.core;

import com.google.gson.Gson;
import com.pr0gramm.app.Lazy;
import com.pr0gramm.app.api.pr0gramm.ApiGsonBuilder;

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
        gson = ApiGsonBuilder.builder().create();
    }

    public static Gson gson() {
        return instance.get().gson;
    }

    static final byte NUMBER_LONG = 1;
    static final byte NUMBER_DOUBLE = 2;
    static final byte NUMBER_FLOAT = 3;
    static final byte NUMBER_INTEGER = 4;
    static final byte NUMBER_BYTE = 5;

    static final byte NAME_FOLLOWING = 1;
    static final byte NAME_REFERENCE = 2;
}
