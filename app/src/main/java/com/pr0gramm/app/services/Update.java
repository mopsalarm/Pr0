package com.pr0gramm.app.services;

import android.os.Parcel;
import android.os.Parcelable;

import org.immutables.gson.Gson;
import org.immutables.value.Value;

/**
 * Update
 */
@Value.Immutable
@Gson.TypeAdapters
public abstract class Update implements Parcelable {
    public abstract int version();

    public abstract String apk();

    public abstract String changelog();


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(version());
        dest.writeString(apk());
        dest.writeString(changelog());
    }

    public static final Creator<Update> CREATOR = new Creator<Update>() {
        public Update createFromParcel(Parcel source) {
            return ImmutableUpdate.builder()
                    .version(source.readInt())
                    .apk(source.readString())
                    .changelog(source.readString())
                    .build();
        }

        public Update[] newArray(int size) {
            return new Update[size];
        }
    };
}
