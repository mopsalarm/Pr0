package com.pr0gramm.app.gparcel;

import android.os.Parcel;
import android.os.Parcelable;

import com.pr0gramm.app.api.pr0gramm.response.Tag;
import com.pr0gramm.app.gparcel.core.ParcelAdapter;

import java.util.List;

/**
 */
public class TagListParcelAdapter extends ParcelAdapter<List<Tag>> {
    public TagListParcelAdapter(List<Tag> values) {
        super(values);
    }

    protected TagListParcelAdapter(Parcel parcel) {
        super(parcel);
    }

    public static final Parcelable.Creator<TagListParcelAdapter> CREATOR = new Creator<TagListParcelAdapter>() {
        @Override
        public TagListParcelAdapter createFromParcel(Parcel source) {
            return new TagListParcelAdapter(source);
        }

        @Override
        public TagListParcelAdapter[] newArray(int size) {
            return new TagListParcelAdapter[size];
        }
    };
}
