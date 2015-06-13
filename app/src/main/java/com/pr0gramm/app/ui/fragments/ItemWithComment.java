package com.pr0gramm.app.ui.fragments;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.base.Optional;

/**
 */
public class ItemWithComment implements Parcelable {
    private final long itemId;
    private final Long commentId;

    public ItemWithComment(long itemId) {
        this.itemId = itemId;
        this.commentId = null;
    }

    public ItemWithComment(long itemId, Long commentId) {
        this.itemId = itemId;
        this.commentId = commentId;
    }

    public long getItemId() {
        return itemId;
    }

    public Optional<Long> getCommentId() {
        return Optional.fromNullable(commentId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(this.itemId);
        dest.writeValue(this.commentId);
    }

    protected ItemWithComment(Parcel in) {
        this.itemId = in.readLong();
        this.commentId = (Long) in.readValue(Long.class.getClassLoader());
    }

    public static final Parcelable.Creator<ItemWithComment> CREATOR = new Parcelable.Creator<ItemWithComment>() {
        public ItemWithComment createFromParcel(Parcel source) {
            return new ItemWithComment(source);
        }

        public ItemWithComment[] newArray(int size) {
            return new ItemWithComment[size];
        }
    };
}
