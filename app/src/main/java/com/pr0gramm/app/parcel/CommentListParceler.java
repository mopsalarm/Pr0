package com.pr0gramm.app.parcel;

import android.os.Parcel;
import android.os.Parcelable;

import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.parcel.core.Parceler;

import java.util.List;

/**
 */
public class CommentListParceler extends Parceler<List<Api.Comment>> {
    public CommentListParceler(List<Api.Comment> values) {
        super(values);
    }

    private CommentListParceler(Parcel parcel) {
        super(parcel);
    }

    public static final Parcelable.Creator<CommentListParceler> CREATOR =
            new LambdaCreator<>(CommentListParceler::new, CommentListParceler[]::new);
}
