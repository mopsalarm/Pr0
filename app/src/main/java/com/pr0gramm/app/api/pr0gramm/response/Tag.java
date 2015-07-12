package com.pr0gramm.app.api.pr0gramm.response;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.primitives.Ints;

/**
 */
public class Tag implements Parcelable {
    private int id;
    private float confidence;
    private String tag;

    public Tag() {
    }

    public long getId() {
        return id;
    }

    public float getConfidence() {
        return confidence;
    }

    public String getTag() {
        return tag;
    }

    @Override
    public int hashCode() {
        return Ints.hashCode(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != Tag.class)
            return false;

        Tag other = (Tag) obj;
        return id == other.id;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.id);
        dest.writeFloat(this.confidence);
        dest.writeString(this.tag);
    }

    protected Tag(Parcel in) {
        this.id = in.readInt();
        this.confidence = in.readFloat();
        this.tag = in.readString();
    }

    public static final Parcelable.Creator<Tag> CREATOR = new Parcelable.Creator<Tag>() {
        public Tag createFromParcel(Parcel source) {
            return new Tag(source);
        }

        public Tag[] newArray(int size) {
            return new Tag[size];
        }
    };
}
