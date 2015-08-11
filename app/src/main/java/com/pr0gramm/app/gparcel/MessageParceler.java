package com.pr0gramm.app.gparcel;

import android.os.Parcel;
import android.os.Parcelable;

import com.pr0gramm.app.api.pr0gramm.response.Message;
import com.pr0gramm.app.gparcel.core.Parceler;

/**
 */
public class MessageParceler extends Parceler<Message> {
    public MessageParceler(Message value) {
        super(value);
    }

    @SuppressWarnings("unused")
    protected MessageParceler(Parcel parcel) {
        super(parcel);
    }

    public static final Parcelable.Creator<MessageParceler> CREATOR =
            new ReflectionCreator<>(MessageParceler.class);
}
