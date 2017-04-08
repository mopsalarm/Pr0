package com.pr0gramm.app.parcel;

import android.os.Parcel;
import android.os.Parcelable;

import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.parcel.core.Parceler;

/**
 */
public class MessageParceler extends Parceler<Api.Message> {
    public MessageParceler(Api.Message value) {
        super(value);
    }

    private MessageParceler(Parcel parcel) {
        super(parcel);
    }

    public static final Parcelable.Creator<MessageParceler> CREATOR =
            new LambdaCreator<>(MessageParceler::new, MessageParceler[]::new);
}
