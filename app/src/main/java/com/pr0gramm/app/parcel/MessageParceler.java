package com.pr0gramm.app.parcel;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.reflect.TypeToken;
import com.pr0gramm.app.api.pr0gramm.response.Message;
import com.pr0gramm.app.parcel.core.Parceler;

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

    @Override
    public TypeToken<Message> getType() {
        return new TypeToken<Message>() {
        };
    }

    public static final Parcelable.Creator<MessageParceler> CREATOR =
            new LambdaCreator<>(MessageParceler::new, MessageParceler[]::new);
}
