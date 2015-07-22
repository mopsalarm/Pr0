package com.pr0gramm.app.gparcel;

import android.os.Parcel;
import android.os.Parcelable;

import com.pr0gramm.app.api.pr0gramm.response.Message;
import com.pr0gramm.app.gparcel.core.ParcelAdapter;

/**
 */
public class MessageParcelAdapter extends ParcelAdapter<Message> {
    public MessageParcelAdapter(Message value) {
        super(value);
    }

    @SuppressWarnings("unused")
    protected MessageParcelAdapter(Parcel parcel) {
        super(parcel);
    }

    public static final Parcelable.Creator<MessageParcelAdapter> CREATOR =
            new ReflectionCreator<>(MessageParcelAdapter.class);
}
