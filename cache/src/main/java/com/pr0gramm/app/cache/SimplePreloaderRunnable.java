package com.pr0gramm.app.cache;

import java.io.IOException;
import java.util.concurrent.Executor;

/**
 */
public class SimplePreloaderRunnable implements Runnable {
    private final Executor executor;
    private final Loader loader;

    private SimplePreloaderRunnable(Executor executor, Loader loader) {
        this.executor = executor;
        this.loader = loader;
    }

    @Override
    public void run() {
        try {
            if (loader.step()) {
                // reschedule as long as this loader is alive
                executor.execute(this);
            }

        } catch (IOException err) {
            // could not preload item
            err.printStackTrace();
        }
    }

    /**
     * Preloads the given loader on the provided executor.
     *
     * @param executor The executor to preload an item on
     * @param loader   The loader that will be used to load data.
     */
    public static void preload(Executor executor, Loader loader) {
        executor.execute(new SimplePreloaderRunnable(executor, loader));
    }
}
