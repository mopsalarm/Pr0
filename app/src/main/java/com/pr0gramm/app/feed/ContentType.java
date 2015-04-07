package com.pr0gramm.app.feed;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.pr0gramm.app.R;

import java.util.Collection;
import java.util.Set;

/**
 * Content type to load.
 */
public enum ContentType implements Parcelable {
    SFW(1, R.string.type_sfw), NSFW(2, R.string.type_nsfw), NSFL(4, R.string.type_nsfl);

    private final int flag;
    private final int title;

    ContentType(int flag, int desc) {
        this.flag = flag;
        this.title = desc;
    }

    public int getTitle() {
        return title;
    }

    public int getFlag() {
        return flag;
    }

    public static int combine(Iterable<ContentType> flags) {
        int sum = 0;
        for (ContentType flag : flags)
            sum += flag.getFlag();

        return sum;
    }

    /**
     * Gets a all the content types that are encoded in the given
     * flags number. This is the reverse of {@link #combine(Iterable)}.
     *
     * @param flags The encoded content types.
     */
    public static Set<ContentType> decompose(int flags) {
        return FluentIterable.of(values())
                .filter(ct -> (ct.flag & flags) != 0)
                .toSet();
    }

    /**
     * Returns the {@link com.pr0gramm.app.feed.ContentType} that matches the given
     * flag's value. There must be only one bit set on the flags parameter.
     * This returns an empty optional, if no content type could be found.
     */
    public static Optional<ContentType> valueOf(int flag) {
        return FluentIterable.of(values()).firstMatch(ct -> ct.flag == flag);
    }

    public static String toString(Context context, Collection<ContentType> types) {
        return FluentIterable.from(types)
                .transform(ContentType::getTitle)
                .transform(context::getString)
                .join(Joiner.on("+"));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(ordinal());
    }

    public static final Parcelable.Creator<ContentType> CREATOR = new Parcelable.Creator<ContentType>() {
        public ContentType createFromParcel(Parcel source) {
            int idx = source.readInt();
            return ContentType.values()[idx];
        }

        public ContentType[] newArray(int size) {
            return new ContentType[size];
        }
    };
}
