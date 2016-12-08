package com.pr0gramm.app.parcel;

import android.os.Parcel;

import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.parcel.core.Parceler;

/**
 */
public class NewCommentParceler extends Parceler<Api.NewComment> {
    public NewCommentParceler(Api.NewComment value) {
        super(value);
    }

    private NewCommentParceler(Parcel parcel) {
        super(parcel);
    }

    public static final Creator<NewCommentParceler> CREATOR =
            new LambdaCreator<>(NewCommentParceler::new, NewCommentParceler[]::new);
}
