package com.pr0gramm.app.parcel.core;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.base.Stopwatch;
import com.google.common.reflect.TypeToken;
import com.pr0gramm.app.GsonModule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 */
public abstract class Parceler<T> implements Parcelable {
    private static final Logger logger = LoggerFactory.getLogger("Parceler");

    private final T value;

    protected Parceler(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    public abstract TypeToken<T> getType();

    public static <R, T extends Parceler<R>> R get(Class<T> clazz, Bundle bundle, String key) {
        T wrapper = bundle.getParcelable(key);
        if (wrapper == null)
            return null;

        if (!clazz.isInstance(wrapper))
            throw new IllegalArgumentException(String.format("Element %s is not of type %s", key, clazz));

        return wrapper.getValue();
    }

    @SuppressLint("NewApi")
    protected Parceler(Parcel parcel) {

        try (ParcelReader reader = new ParcelReader(parcel)) {
            Stopwatch watch = Stopwatch.createStarted();
            value = GsonModule.INSTANCE.fromJson(reader, getType().getType());
            logger.info("reading of {} took {}", getType(), watch);
        } catch (IOException ioError) {
            throw new RuntimeException("Could not read gson as parce", ioError);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @SuppressLint("NewApi")
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        try (ParcelWriter writer = new ParcelWriter(dest)) {
            Stopwatch watch = Stopwatch.createStarted();
            GsonModule.INSTANCE.toJson(value, getType().getType(), writer);
            logger.info("writing of {} took {}", getType(), watch);
        } catch (IOException ioError) {
            throw new RuntimeException("Could not adapt gson to parcel", ioError);
        }
    }
}
