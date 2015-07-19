package com.pr0gramm.app.gparcel.core;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.base.Stopwatch;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 */
public abstract class ParcelAdapter<T> implements Parcelable {
    private static final Logger logger = LoggerFactory.getLogger(ParcelAdapter.class);

    private final TypeToken<T> type = new TypeToken<T>(getClass()) {
    };

    private final T value;

    protected ParcelAdapter(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    public TypeToken<T> getType() {
        return type;
    }

    public static <R, T extends ParcelAdapter<R>> R get(Class<T> clazz, Bundle bundle, String key) {
        T wrapper = bundle.getParcelable(key);
        if (!clazz.isInstance(wrapper))
            throw new IllegalArgumentException(String.format("Element %s is not of type %s", key, clazz));

        return wrapper.getValue();
    }

    @SuppressLint("NewApi")
    protected ParcelAdapter(Parcel parcel) {
        Gson gson = ParcelContext.gson();

        try (ParcelReader reader = new ParcelReader(parcel)) {
            Stopwatch watch = Stopwatch.createStarted();
            value = gson.fromJson(reader, type.getType());
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
        Gson gson = ParcelContext.gson();

        try (ParcelWriter writer = new ParcelWriter(dest)) {
            Stopwatch watch = Stopwatch.createStarted();
            gson.toJson(value, type.getType(), writer);
            logger.info("writing of {} took {}", getType(), watch);
        } catch (IOException ioError) {
            throw new RuntimeException("Could not adapt gson to parcel", ioError);
        }
    }
}
