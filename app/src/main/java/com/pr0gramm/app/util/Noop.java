package com.pr0gramm.app.util;

import android.content.DialogInterface;
import android.view.View;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Response;

/**
 */
public final class Noop implements DialogInterface.OnClickListener,
        View.OnClickListener,
        Interceptor, Runnable {

    private Noop() {
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {

    }

    @Override
    public void onClick(View v) {

    }

    public static final Noop noop = new Noop();

    @Override
    public Response intercept(Chain chain) throws IOException {
        return chain.proceed(chain.request());
    }

    @Override
    public void run() {

    }
}
