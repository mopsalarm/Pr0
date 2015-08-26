package com.pr0gramm.app.util;

import com.google.common.base.Supplier;

/**
 */
public abstract class Lazy<T> {
    private T value;

    public T get() {
        T result = value;
        if (result == null) {
            synchronized (this) {
                result = value;
                if (result == null) {
                    value = result = compute();
                }
            }
        }

        return result;
    }

    protected abstract T compute();

    /**
     * Creates a new lazy from a supplier.
     */
    public static <T> Lazy<T> of(Supplier<T> supplier) {
        return new Lazy<T>() {
            @Override
            protected T compute() {
                return supplier.get();
            }
        };
    }
}
