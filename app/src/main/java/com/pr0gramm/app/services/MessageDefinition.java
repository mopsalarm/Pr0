package com.pr0gramm.app.services;

import android.app.Dialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;

import com.google.common.base.Optional;
import com.pr0gramm.app.R;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.MainActivity;

import org.immutables.gson.Gson;
import org.immutables.value.Value;

import java.util.List;

/**
 * Maps to a message definition in json.
 */
@Value.Immutable
@Gson.TypeAdapters
public abstract class MessageDefinition {
    /**
     * Unique id of a message
     */
    public abstract int uniqueId();

    public abstract List<Object> condition();

    public abstract String title();

    public abstract String message();

    public abstract Optional<String> okayText();

    public abstract Optional<String> okayLink();

    public abstract Optional<String> cancelText();

    @Value.Default
    public boolean notification() {
        return false;
    }


    public Notification asNotification(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(Intent.ACTION_MAIN);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_notify_new_message)
                .setContentTitle(title())
                .setContentText(message())
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();
    }

    public Dialog asDialog(Context context) {
        DialogBuilder.OnClickListener positiveCallback = dialog -> {
            if (okayLink().isPresent()) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(okayLink().get()));
                context.startActivity(intent);
            }
        };

        return DialogBuilder.start(context)
                .content(message())
                .positive(okayText().or(context.getString(R.string.okay)), positiveCallback)
                .negative(cancelText().orNull())
                .build();
    }
}
