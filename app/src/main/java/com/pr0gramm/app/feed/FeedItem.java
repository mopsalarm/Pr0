package com.pr0gramm.app.feed;

import android.os.Parcel;
import android.os.Parcelable;

import com.pr0gramm.app.api.pr0gramm.response.Feed;

import org.joda.time.Instant;

/**
 * This is an item in pr0gramm feed item to be displayed. It is backed
 * by the data of an {@link com.pr0gramm.app.api.pr0gramm.response.Feed.Item}.
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
    private final int mark;
    private final Instant created;

    public FeedItem(Feed.Item item) {
        id = item.getId();
        promotedId = item.getPromoted();
        thumb = item.getThumb();
        image = item.getImage();
        fullsize = item.getFullsize();
        user = item.getUser();
        up = item.getUp();
        down = item.getDown();
        mark = item.getMark();
        created = item.getCreated();
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

    public int getMark() {
        return mark;
    }

    public Instant getCreated() {
        return created;
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
        // combine up/down as rating.
        int rating = ((up << 16) & 0xffff0000) | (down & 0xffff);

        dest.writeInt((int) this.id);
        dest.writeInt((int) this.promotedId);
        dest.writeString(this.thumb);
        dest.writeString(this.image);
        dest.writeString(this.fullsize);
        dest.writeString(this.user);
        dest.writeInt(rating);
        dest.writeByte((byte) mark);
        dest.writeInt((int) (created.getMillis() / 1000));
    }

    private FeedItem(Parcel in) {
        this.id = in.readInt();
        this.promotedId = in.readInt();
        this.thumb = in.readString();
        this.image = in.readString();
        this.fullsize = in.readString();
        this.user = in.readString();
        int rating = in.readInt();
        this.mark = in.readByte();
        this.created = new Instant(1000L * in.readInt());

        // extract up/down from rating
        this.up = (rating >> 16) & 0xffff;
        this.down = rating & 0xffff;
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
