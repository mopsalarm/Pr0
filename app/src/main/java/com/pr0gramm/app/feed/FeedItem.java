package com.pr0gramm.app.feed;

import android.os.Parcel;
import android.os.Parcelable;

import com.pr0gramm.app.api.Feed;

/**
 * This is an item in pr0gramm feed item to be displayed. It is backed
 * by a {@link com.pr0gramm.app.api.Feed.Item} and enhanced with meta data,
 * like "already seen".
 */
public class FeedItem implements Parcelable {
    private final long id;
    private final long promotedId;
    private final String thumb;
    private final String image;
    private final String fullsize;
    private final String user;
    private final int up;
    private final int down;
    private final boolean seen;

    public FeedItem(Feed.Item item, boolean seen) {
        id = item.getId();
        promotedId = item.getPromoted();
        thumb = item.getThumb();
        image = item.getImage();
        fullsize = item.getFullsize();
        user = item.getUser();
        up = item.getUp();
        down = item.getDown();

        this.seen = seen;
    }

    public boolean isSeen() {
        return seen;
    }

    public long getId() {
        return id;
    }

    public long getPromotedId() {
        return promotedId;
    }

    public String getThumb() {
        return thumb;
    }

    public String getImage() {
        return image;
    }

    public String getFullsize() {
        return fullsize;
    }

    public String getUser() {
        return user;
    }

    public int getUp() {
        return up;
    }

    public int getDown() {
        return down;
    }

    /**
     * Gets the id of this feed item depending on the type of the feed..
     *
     * @param type The type of feed.
     */
    public long getId(FeedType type) {
        return type == FeedType.PROMOTED ? promotedId : id;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(this.id);
        dest.writeLong(this.promotedId);
        dest.writeString(this.thumb);
        dest.writeString(this.image);
        dest.writeString(this.fullsize);
        dest.writeString(this.user);
        dest.writeInt(this.up);
        dest.writeInt(this.down);
        dest.writeByte(seen ? (byte) 1 : (byte) 0);
    }

    private FeedItem(Parcel in) {
        this.id = in.readLong();
        this.promotedId = in.readLong();
        this.thumb = in.readString();
        this.image = in.readString();
        this.fullsize = in.readString();
        this.user = in.readString();
        this.up = in.readInt();
        this.down = in.readInt();
        this.seen = in.readByte() != 0;
    }

    public static final Parcelable.Creator<FeedItem> CREATOR = new Parcelable.Creator<FeedItem>() {
        public FeedItem createFromParcel(Parcel source) {
            return new FeedItem(source);
        }

        public FeedItem[] newArray(int size) {
            return new FeedItem[size];
        }
    };
}
