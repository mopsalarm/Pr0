package com.pr0gramm.app.parcel;

import android.os.Parcel;

import com.google.common.reflect.TypeToken;
import com.pr0gramm.app.api.pr0gramm.response.NewComment;
import com.pr0gramm.app.parcel.core.Parceler;

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

    @Override
    public TypeToken<NewComment> getType() {
        return new TypeToken<NewComment>() {
        };
    }

    public static final Creator<NewCommentParceler> CREATOR =
            new LambdaCreator<>(NewCommentParceler::new, NewCommentParceler[]::new);
}
