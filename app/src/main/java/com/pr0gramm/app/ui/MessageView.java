package com.pr0gramm.app.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;
import com.google.common.base.Ascii;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.response.Message;
import com.pr0gramm.app.ui.views.SenderInfoView;
import com.pr0gramm.app.util.AndroidUtility;
import com.pr0gramm.app.util.Lazy;
import com.squareup.picasso.Picasso;

import org.joda.time.Hours;
import org.joda.time.Instant;

import static org.joda.time.Instant.now;

/**
 */
public class MessageView extends RelativeLayout {
    private final Lazy<TextDrawable.IShapeBuilder> textShapeBuilder = Lazy.of(() ->
            TextDrawable.builder().beginConfig()
                    .textColor(getContext().getResources().getColor(R.color.feed_background))
                    .fontSize(AndroidUtility.dp(getContext(), 24))
                    .bold()
                    .endConfig());

    private final TextView text;
    private final TextView type;
    private final ImageView image;
    private final SenderInfoView sender;

    private final Picasso picasso;

    private final Instant scoreVisibleThreshold = now().minus(Hours.ONE.toStandardDuration());

    public MessageView(Context context) {
        this(context, null, 0);
    }

    public MessageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MessageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);


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

        picasso = isInEditMode() ? null :
                Dagger.appComponent(context).picasso();

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

    public void update(Message message) {
        update(message, null);
    }

    public void update(Message message, @Nullable String name) {
        // set the type. if we have an item, we  have a comment
        boolean isComment = message.getItemId() != 0;
        if (type != null) {
            type.setText(isComment
                    ? getContext().getString(R.string.inbox_message_comment)
                    : getContext().getString(R.string.inbox_message_private));
        }

        // the text of the message
        AndroidUtility.linkify(text, message.getMessage());


        // draw the image for this post
        if (isComment) {
            String url = "http://thumb.pr0gramm.com/" + message.getThumb();
            picasso.load(url).into(image);
        } else {
            picasso.cancelRequest(image);

            // set a colored drawable with the first two letters of the user
            image.setImageDrawable(makeSenderDrawable(message));
        }

        // show the points
        boolean visible = (name != null && Ascii.equalsIgnoreCase(message.getName(), name))
                || message.getCreated().isBefore(scoreVisibleThreshold);

        // sender info
        sender.setSenderName(message.getName(), message.getMark());
        sender.setDate(message.getCreated());

        if (isComment && !visible) {
            sender.setPointsUnknown();
        } else if (isComment) {
            sender.setPoints(message.getScore());
        } else {
            sender.hidePointView();
        }
    }

    private TextDrawable makeSenderDrawable(Message message) {
        String name = message.getName();

        StringBuilder text = new StringBuilder();
        text.append(Character.toUpperCase(name.charAt(0)));
        if (name.length() > 1) {
            text.append(Character.toLowerCase(name.charAt(1)));
        }

        int color = ColorGenerator.MATERIAL.getColor(message.getSenderId());
        return textShapeBuilder.get().buildRect(text.toString(), color);
    }
}
