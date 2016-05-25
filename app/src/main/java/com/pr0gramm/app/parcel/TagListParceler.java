package com.pr0gramm.app.parcel;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.reflect.TypeToken;
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
    protected TagListParceler(Parcel parcel) {
        super(parcel);
    }

    @Override
    public TypeToken<List<Api.Tag>> getType() {
        return new TypeToken<List<Api.Tag>>() {
        };
    }

    public static final Parcelable.Creator<TagListParceler> CREATOR =
            new LambdaCreator<>(TagListParceler::new, TagListParceler[]::new);
}
