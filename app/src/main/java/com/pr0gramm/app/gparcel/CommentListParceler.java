package com.pr0gramm.app.gparcel;

import android.os.Parcel;
import android.os.Parcelable;

import com.pr0gramm.app.api.pr0gramm.response.Comment;
import com.pr0gramm.app.gparcel.core.Parceler;

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

    public static final Parcelable.Creator<CommentListParceler> CREATOR =
            new ReflectionCreator<>(CommentListParceler.class);
}
