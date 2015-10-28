package com.pr0gramm.app.parcel;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.reflect.TypeToken;
import com.pr0gramm.app.api.pr0gramm.response.Comment;
import com.pr0gramm.app.parcel.core.Parceler;

import java.util.List;

/**
 */
public class CommentListParceler extends Parceler<List<Comment>> {
    public CommentListParceler(List<Comment> values) {
        super(values);
    }

    @SuppressWarnings("unused")
    protected CommentListParceler(Parcel parcel) {
        super(parcel);
    }

    @Override
    public TypeToken<List<Comment>> getType() {
        return new TypeToken<List<Comment>>() {
        };
    }

    public static final Parcelable.Creator<CommentListParceler> CREATOR =
            new LambdaCreator<>(CommentListParceler::new, CommentListParceler[]::new);
}
