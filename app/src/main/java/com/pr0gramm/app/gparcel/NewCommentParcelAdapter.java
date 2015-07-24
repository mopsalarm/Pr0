package com.pr0gramm.app.gparcel;

import android.os.Parcel;

import com.pr0gramm.app.api.pr0gramm.response.NewComment;
import com.pr0gramm.app.gparcel.core.ParcelAdapter;

/**
 */
public class NewCommentParcelAdapter extends ParcelAdapter<NewComment> {
    public NewCommentParcelAdapter(NewComment value) {
        super(value);
    }

    @SuppressWarnings("unused")
    protected NewCommentParcelAdapter(Parcel parcel) {
        super(parcel);
    }

    public static final Creator<NewCommentParcelAdapter> CREATOR =
            new ReflectionCreator<>(NewCommentParcelAdapter.class);
}
