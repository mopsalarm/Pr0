package com.pr0gramm.app.util;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;
import com.google.common.base.Supplier;

public abstract class SimpleJobCreator implements JobCreator {
    private final String tag;

    protected SimpleJobCreator(String tag) {
        this.tag = tag;
    }

    @Override
    public final Job create(String s) {
        if (tag.equals(s)) {
            return create();
        } else {
            return null;
        }
    }

    /**
     * Create the real job.
     */
    protected abstract Job create();

    public static JobCreator forSupplier(String tag, Supplier<? extends Job> creator) {
        return new SimpleJobCreator(tag) {
            @Override
            protected Job create() {
                return creator.get();
            }
        };
    }
}
