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

    @SuppressWarnings("unused")
    protected TagListParcelAdapter(Parcel parcel) {
        super(parcel);
    }

    public static final Parcelable.Creator<TagListParcelAdapter> CREATOR =
            new ReflectionCreator<>(TagListParcelAdapter.class);
}
