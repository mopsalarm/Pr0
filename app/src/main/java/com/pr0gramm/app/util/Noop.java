package com.pr0gramm.app.util;

import android.content.DialogInterface;
import android.view.View;

/**
 */
public final class Noop implements DialogInterface.OnClickListener, View.OnClickListener {
    private Noop() {
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {

    }

    @Override
    public void onClick(View v) {

    }

    public static final Noop noop = new Noop();
}
