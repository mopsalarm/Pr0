package com.pr0gramm.app.parcel;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.base.Function;

/**
 */
public class LambdaCreator<T extends Parcelable> implements Parcelable.Creator<T> {
    private final Function<Parcel, T> createFromParcel;
    private final Function<Integer, T[]> newArray;

    public LambdaCreator(Function<Parcel, T> createFromParcel, Function<Integer, T[]> newArray) {
        this.createFromParcel = createFromParcel;
        this.newArray = newArray;
    }

    @Override
    public T createFromParcel(Parcel source) {
        return createFromParcel.apply(source);
    }

    @Override
    public T[] newArray(int size) {
        return newArray.apply(size);
    }
}
