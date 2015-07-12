package com.pr0gramm.app.api.pr0gramm.response;

import android.os.Parcel;
import android.os.Parcelable;

import org.joda.time.Instant;

/**
 */
@SuppressWarnings("unused")
public class Comment implements Parcelable {
    private long id;
    private float confidence;
    private String name;
    private String content;
    private Instant created;
    private long parent;
    private int up, down;
    private int mark;

    public Comment() {
    }

    public long getId() {
        return id;
    }

    public float getConfidence() {
        return confidence;
    }

    public String getName() {
        return name;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreated() {
        return created;
    }

    public long getParent() {
        return parent;
    }

    public int getUp() {
        return up;
    }

    public int getDown() {
        return down;
    }

    public int getMark() {
        return mark;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(this.id);
        dest.writeFloat(this.confidence);
        dest.writeString(this.name);
        dest.writeString(this.content);
        dest.writeSerializable(this.created);
        dest.writeLong(this.parent);
        dest.writeInt(this.up);
        dest.writeInt(this.down);
        dest.writeInt(this.mark);
    }

    protected Comment(Parcel in) {
        this.id = in.readLong();
        this.confidence = in.readFloat();
        this.name = in.readString();
        this.content = in.readString();
        this.created = (Instant) in.readSerializable();
        this.parent = in.readLong();
        this.up = in.readInt();
        this.down = in.readInt();
        this.mark = in.readInt();
    }

    public static final Creator<Comment> CREATOR = new Creator<Comment>() {
        public Comment createFromParcel(Parcel source) {
            return new Comment(source);
        }

        public Comment[] newArray(int size) {
            return new Comment[size];
        }
    };
}
