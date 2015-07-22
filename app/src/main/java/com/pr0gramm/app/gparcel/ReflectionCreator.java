package com.pr0gramm.app.gparcel;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.base.Throwables;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;

/**
 */
public class ReflectionCreator<T extends Parcelable> implements Parcelable.Creator<T> {
    private final Class<T> valueClass;
    private final Constructor<T> constructor;

    public ReflectionCreator(Class<T> valueClass) {
        checkArgument(!Modifier.isAbstract(valueClass.getModifiers()),
                "Class must not be abstract");

        this.valueClass = valueClass;

        try {
            constructor = valueClass.getDeclaredConstructor(Parcel.class);
            constructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw Throwables.propagate(e);
        }
    }

    @SuppressLint("NewApi")
    @Override
    public T createFromParcel(Parcel source) {
        try {
            return constructor.newInstance(source);

        } catch (InstantiationException | IllegalAccessException error) {
            throw Throwables.propagate(error);

        } catch (InvocationTargetException error) {
            throw Throwables.propagate(firstNonNull(error.getTargetException(), error));
        }
    }

    @Override
    public T[] newArray(int size) {
        //noinspection unchecked
        return (T[]) Array.newInstance(valueClass, size);
    }
}
