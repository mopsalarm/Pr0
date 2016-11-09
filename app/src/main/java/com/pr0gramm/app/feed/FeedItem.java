package com.pr0gramm.app.feed;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.services.HasThumbnail;

import org.joda.time.Instant;

/**
 * This is an item in pr0gramm feed item to be displayed. It is backed
 * by the data of an {@link Api.Feed.Item}.
 */
public class FeedItem implements Parcelable, HasThumbnail {
    private final Instant created;
    private final String thumb;
    private final String image;
    private final String fullsize;
    private final String user;
    private final int id;
    private final int promotedId;
    private final int width, height;
    private final short up;
    private final short down;
    private final byte mark;
    private final byte flags;
    private final boolean audio;

    public FeedItem(Api.Feed.Item item) {
        id = (int) item.getId();
        promotedId = (int) item.getPromoted();
        thumb = item.getThumb();
        image = item.getImage();
        fullsize = item.getFullsize();
        user = item.getUser();
        up = (short) item.getUp();
        down = (short) item.getDown();
        mark = (byte) item.getMark();
        created = item.getCreated();
        flags = (byte) item.getFlags();
        width = item.width().or(0);
        height = item.height().or(0);
        audio = item.audio().or(false);
    }

    @Override
    public long id() {
        return id;
    }

    public long promotedId() {
        return promotedId;
    }

    @Override
    @NonNull
    public String thumbnail() {
        return thumb;
    }

    public String image() {
        return image;
    }

    public String fullsize() {
        return fullsize;
    }

    public String user() {
        return user;
    }

    public int up() {
        return up;
    }

    public int down() {
        return down;
    }

    public int mark() {
        return mark;
    }

    public Instant created() {
        return created;
    }

    public int flags() {
        return flags;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean audio() {
        return audio;
    }

    /**
     * Returns the content type of this Item, falling back to {@link ContentType#SFW}
     * if no type is available.
     */
    public ContentType contentType() {
        return ContentType.valueOf(flags).or(ContentType.SFW);
    }

    /**
     * Gets the id of this feed item depending on the type of the feed..
     *
     * @param type The type of feed.
     */
    public long id(FeedType type) {
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

        dest.writeInt(this.id);
        dest.writeInt(this.promotedId);
        dest.writeString(this.thumb);
        dest.writeString(this.image);
        dest.writeString(this.fullsize);
        dest.writeString(this.user);
        dest.writeInt(rating);
        dest.writeByte(mark);
        dest.writeInt((int) (created.getMillis() / 1000));
        dest.writeByte(this.flags);
        dest.writeInt(width);
        dest.writeInt(height);
        dest.writeByte(audio ? (byte) 1 : 0);
    }

    FeedItem(Parcel in) {
        this.id = in.readInt();
        this.promotedId = in.readInt();
        this.thumb = in.readString();
        this.image = in.readString();
        this.fullsize = in.readString();
        this.user = in.readString();
        int rating = in.readInt();
        this.mark = in.readByte();
        this.created = new Instant(1000L * in.readInt());
        this.flags = in.readByte();
        this.width = in.readInt();
        this.height = in.readInt();
        this.audio = in.readByte() != 0;

        // extract up/down from rating
        this.up = (short) ((rating >> 16) & 0xffff);
        this.down = (short) (rating & 0xffff);
    }

    public boolean isVideo() {
        return image.endsWith(".webm") || image.endsWith(".mp4");
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
