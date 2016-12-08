package com.pr0gramm.app.parcel.core;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.base.Stopwatch;
import com.google.common.reflect.TypeToken;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.GsonModule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 */
public abstract class Parceler<T> implements Parcelable {
    private static final Logger logger = LoggerFactory.getLogger("Parceler");

    private final TypeToken<T> typeToken = new TypeToken<T>(getClass()) {
    };

    private final T value;

    protected Parceler(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    public TypeToken<T> getType() {
        return typeToken;
    }

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
        Stopwatch watch = Stopwatch.createStarted();

        // read binary data from the parcel
        byte[] input = parcel.createByteArray();
        try (BinaryReader reader = BinaryReader.from(input)) {
            value = GsonModule.INSTANCE.fromJson(reader, getType().getType());

            if (BuildConfig.DEBUG) {
                logger.info("reading of {} took {} ({} bytes)", getType(), watch, input.length);
            }

        } catch (IOException ioError) {
            throw new RuntimeException("Could not read gson as parcel", ioError);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @SuppressLint("NewApi")
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Stopwatch watch = Stopwatch.createStarted();

        BinaryWriter writer = new BinaryWriter();
        GsonModule.INSTANCE.toJson(value, getType().getType(), writer);
        writer.close();

        // now write serialized data to the parcel
        byte[] output = writer.toByteArray();
        dest.writeByteArray(output);

        if (BuildConfig.DEBUG) {
            logger.info("writing of {} took {} ({} bytes)", getType(), watch, output.length);
        }
    }
}
