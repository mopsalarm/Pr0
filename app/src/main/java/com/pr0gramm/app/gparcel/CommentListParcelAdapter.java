package com.pr0gramm.app.gparcel;

import android.os.Parcel;
import android.os.Parcelable;

import com.pr0gramm.app.api.pr0gramm.response.Comment;
import com.pr0gramm.app.gparcel.core.ParcelAdapter;

import java.util.List;

/**
 */
public class CommentListParcelAdapter extends ParcelAdapter<List<Comment>> {
    public CommentListParcelAdapter(List<Comment> values) {
        super(values);
    }

    @SuppressWarnings("unused")
    protected CommentListParcelAdapter(Parcel parcel) {
        super(parcel);
    }

    public static final Parcelable.Creator<CommentListParcelAdapter> CREATOR =
            new ReflectionCreator<>(CommentListParcelAdapter.class);
}
