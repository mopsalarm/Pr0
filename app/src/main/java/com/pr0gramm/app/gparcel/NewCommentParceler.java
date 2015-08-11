package com.pr0gramm.app.gparcel;

import android.os.Parcel;

import com.pr0gramm.app.api.pr0gramm.response.NewComment;
import com.pr0gramm.app.gparcel.core.Parceler;

/**
 */
public class NewCommentParceler extends Parceler<NewComment> {
    public NewCommentParceler(NewComment value) {
        super(value);
    }

    @SuppressWarnings("unused")
    protected NewCommentParceler(Parcel parcel) {
        super(parcel);
    }

    public static final Creator<NewCommentParceler> CREATOR =
            new ReflectionCreator<>(NewCommentParceler.class);
}
