package com.pr0gramm.app.parcel;

import android.os.Parcel;
import android.os.Parcelable;

import com.pr0gramm.app.api.pr0gramm.response.Tag;
import com.pr0gramm.app.parcel.core.Parceler;

import java.util.List;

/**
 */
public class TagListParceler extends Parceler<List<Tag>> {
    public TagListParceler(List<Tag> values) {
        super(values);
    }

    @SuppressWarnings("unused")
    protected TagListParceler(Parcel parcel) {
        super(parcel);
    }

    public static final Parcelable.Creator<TagListParceler> CREATOR =
            new ReflectionCreator<>(TagListParceler.class);
}
