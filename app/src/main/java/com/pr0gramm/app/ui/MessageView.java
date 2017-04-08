package com.pr0gramm.app.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.common.base.Ascii;
import com.pr0gramm.app.AppComponent;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.ui.views.SenderInfoView;
import com.pr0gramm.app.util.AndroidUtility;
import com.pr0gramm.app.util.SenderDrawableProvider;
import com.squareup.picasso.Picasso;

import org.joda.time.Hours;
import org.joda.time.Instant;

import static org.joda.time.Instant.now;

/**
 */
public class MessageView extends RelativeLayout {
    private final boolean admin;
    private final TextView text;
    private final TextView type;
    private final ImageView image;
    private final SenderInfoView sender;

    private final Picasso picasso;
    private final SenderDrawableProvider senderDrawableProvider;

    private final Instant scoreVisibleThreshold = now().minus(Hours.ONE.toStandardDuration());

    public MessageView(Context context) {
        this(context, null, 0);
    }

    public MessageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MessageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        this.senderDrawableProvider = new SenderDrawableProvider(context);

        int layoutId = R.layout.message_view;
        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.MessageView,
                    0, 0);

            try {
                layoutId = a.getResourceId(R.styleable.MessageView_viewLayout, layoutId);
            } finally {
                a.recycle();
            }
        }

        inflate(context, layoutId, this);

        if (!isInEditMode()) {
            AppComponent appComponent = Dagger.appComponent(context);
            admin = appComponent.userService().getUserIsAdmin();
            picasso = appComponent.picasso();
        } else {
            admin = false;
            picasso = null;
        }

        text = (TextView) findViewById(R.id.message_text);
        type = (TextView) findViewById(R.id.message_type);
        image = (ImageView) findViewById(R.id.message_image);
        sender = (SenderInfoView) findViewById(R.id.message_sender_info);
    }

    public void setAnswerClickedListener(OnClickListener listener) {
        sender.setOnAnswerClickedListener(listener);
    }

    public void setOnSenderClickedListener(OnClickListener listener) {
        sender.setOnSenderClickedListener(listener);
    }

    public void update(Api.Message message) {
        update(message, null);
    }

    public void update(Api.Message message, @Nullable String name) {
        update(message, name, PointsVisibility.CONDITIONAL);
    }

    public void update(Api.Message message, @Nullable String name, PointsVisibility pointsVisibility) {
        // set the type. if we have an item, we  have a comment
        boolean isComment = message.itemId() != 0;
        if (type != null) {
            type.setText(isComment
                    ? getContext().getString(R.string.inbox_message_comment)
                    : getContext().getString(R.string.inbox_message_private));
        }

        // the text of the message
        AndroidUtility.linkify(text, message.message());

        // draw the image for this post
        if (isComment) {
            String url = "http://thumb.pr0gramm.com/" + message.thumbnail();
            picasso.load(url).into(image);
        } else {
            picasso.cancelRequest(image);

            // set a colored drawable with the first two letters of the user
            image.setImageDrawable(senderDrawableProvider.makeSenderDrawable(message));
        }

        // show the points
        boolean visible = (name != null && Ascii.equalsIgnoreCase(message.name(), name))
                || message.creationTime().isBefore(scoreVisibleThreshold);

        // sender info
        sender.setSenderName(message.name(), message.mark());
        sender.setDate(message.creationTime());

        if ((admin || pointsVisibility != PointsVisibility.NEVER) && isComment) {
            if (admin || pointsVisibility == PointsVisibility.ALWAYS || visible) {
                sender.setPoints(message.score());
            } else {
                sender.setPointsUnknown();
            }
        } else {
            sender.hidePointView();
        }
    }

    public enum PointsVisibility {
        ALWAYS, CONDITIONAL, NEVER
    }
}
