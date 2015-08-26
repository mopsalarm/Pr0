package com.pr0gramm.app.parcel.core;

import android.os.Parcel;
import android.os.Parcelable;

import rx.functions.Func1;

/**
 */
public class ParcelableCreator<T> implements Parcelable.Creator<T> {
    private final Func1<Parcel, T> createFromParcel;
    private final Func1<Integer, T[]> newArray;

    public ParcelableCreator(Func1<Parcel, T> createFromParcel, Func1<Integer, T[]> newArray) {
        this.createFromParcel = createFromParcel;
        this.newArray = newArray;
    }

    public T createFromParcel(Parcel in) {
        return createFromParcel.call(in);
    }

    public T[] newArray(int size) {
        return newArray.call(size);
    }
}
