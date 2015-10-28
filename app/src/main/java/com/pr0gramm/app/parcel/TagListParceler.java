package com.pr0gramm.app.parcel;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.reflect.TypeToken;
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

    @Override
    public TypeToken<List<Tag>> getType() {
        return new TypeToken<List<Tag>>() {
        };
    }

    public static final Parcelable.Creator<TagListParceler> CREATOR =
            new LambdaCreator<>(TagListParceler::new, TagListParceler[]::new);
}
