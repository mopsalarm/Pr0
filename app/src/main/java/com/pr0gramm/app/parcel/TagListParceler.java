package com.pr0gramm.app.parcel;

import android.os.Parcel;
import android.os.Parcelable;

import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.parcel.core.Parceler;

import java.util.List;

/**
 */
public class TagListParceler extends Parceler<List<Api.Tag>> {
    public TagListParceler(List<Api.Tag> values) {
        super(values);
    }

    @SuppressWarnings("unused")
    private TagListParceler(Parcel parcel) {
        super(parcel);
    }

    public static final Parcelable.Creator<TagListParceler> CREATOR =
            new LambdaCreator<>(TagListParceler::new, TagListParceler[]::new);
}
