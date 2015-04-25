package com.pr0gramm.app;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.LayoutRes;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;

/**
 * Wrapper around {@link android.support.v7.app.AlertDialog.Builder}
 */
public class DialogBuilder {
    private final Context context;
    private String positive;
    private String neutral;
    private String negative;

    private String content;
    private View view;
    private String title;
    private OnClickListener positiveOnClick;
    private OnClickListener negativeOnClick;
    private OnClickListener neutralOnClick;
    private boolean autoDismiss = true;
    private boolean cancelable = true;
    private DialogInterface.OnShowListener onShowListener;

    private DialogBuilder(Context context) {
        this.context = context;
    }

    public DialogBuilder content(String content) {
        this.content = content;
        return this;
    }

    public DialogBuilder content(@StringRes int content, Object... args) {
        return content(getString(content, args));
    }

    public DialogBuilder title(String title) {
        this.title = title;
        return this;
    }

    public DialogBuilder title(@StringRes int title, Object... args) {
        return title(getString(title, args));
    }

    public DialogBuilder noAutoDismiss() {
        autoDismiss = false;
        return this;
    }

    public DialogBuilder content(View view) {
        return content(view, true);
    }

    public DialogBuilder layout(@LayoutRes int view) {
        return content(LayoutInflater.from(context).inflate(view, null));
    }

    public DialogBuilder positive(@StringRes int text) {
        return positive(getString(text), null);
    }

    public DialogBuilder positive(@StringRes int text, OnClickListener onClick) {
        return positive(getString(text), onClick);
    }

    public DialogBuilder positive(@StringRes int text, Runnable onClick) {
        return positive(getString(text), dialog -> onClick.run());
    }

    public DialogBuilder positive(String text) {
        return positive(text, null);
    }

    public DialogBuilder positive(String text, OnClickListener onClick) {
        this.positive = text;
        this.positiveOnClick = onClick;
        return this;
    }

    public DialogBuilder negative(@StringRes int text) {
        return negative(getString(text), null);
    }

    public DialogBuilder negative(@StringRes int text, OnClickListener onClick) {
        return negative(getString(text), onClick);
    }

    public DialogBuilder negative(@StringRes int text, Runnable onClick) {
        return negative(getString(text), dialog -> onClick.run());
    }

    public DialogBuilder negative(String text) {
        return negative(text, null);
    }

    public DialogBuilder negative(String text, OnClickListener onClick) {
        this.negative = text;
        this.negativeOnClick = onClick;
        return this;
    }

    public DialogBuilder neutral(@StringRes int text) {
        return neutral(getString(text), null);
    }

    public DialogBuilder neutral(@StringRes int text, OnClickListener onClick) {
        return neutral(getString(text), onClick);
    }

    public DialogBuilder neutral(@StringRes int text, Runnable onClick) {
        return neutral(getString(text), dialog -> onClick.run());
    }

    public DialogBuilder neutral(String text) {
        return neutral(text, null);
    }

    public DialogBuilder neutral(String text, OnClickListener onClick) {
        this.neutral = text;
        this.neutralOnClick = onClick;
        return this;
    }

    public DialogBuilder content(View view, boolean wrapInScroll) {
        if(wrapInScroll) {
            ScrollView scroll = new ScrollView(context);
            scroll.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            scroll.addView(view);
            this.view = scroll;
        } else {
            this.view = view;
        }

        return this;
    }

    private String getString(@StringRes int content) {
        return context.getString(content);
    }

    private String getString(@StringRes int content, Object[] args) {
        return context.getString(content, args);
    }

    public Dialog show() {
        Dialog dialog = build();
        dialog.show();

        return dialog;
    }

    public Dialog build() {
        int theme = R.style.Theme_AppCompat_Light_Dialog;
        AlertDialog.Builder builder = new AlertDialog.Builder(context, theme)
                .setTitle(title)
                .setMessage(content)
                .setView(view)
                .setCancelable(cancelable);

        if (positive != null)
            builder.setPositiveButton(positive, null);

        if (negative != null)
            builder.setNegativeButton(negative, null);

        if (neutral != null)
            builder.setNeutralButton(neutral, null);


        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(di -> {
            for (int button : BUTTONS) {
                Button btn = dialog.getButton(button);
                if (btn != null) {
                    btn.setOnClickListener(v -> onButtonClicked(button, dialog));
                }
            }

            if(onShowListener != null)
                onShowListener.onShow(dialog);
        });

        return dialog;
    }

    private void onButtonClicked(int button, AlertDialog dialog) {
        if (button == Dialog.BUTTON_POSITIVE && positiveOnClick != null)
            positiveOnClick.onClick(dialog);

        if (button == Dialog.BUTTON_NEGATIVE && negativeOnClick != null)
            negativeOnClick.onClick(dialog);

        if (button == Dialog.BUTTON_NEUTRAL && neutralOnClick != null)
            neutralOnClick.onClick(dialog);

        if (autoDismiss)
            dialog.dismiss();
    }

    public static DialogBuilder start(Context context) {
        return new DialogBuilder(context);
    }

    public DialogBuilder cancelable(boolean cancelable) {
        this.cancelable = cancelable;
        return this;
    }

    public DialogBuilder onShow(Dialog.OnShowListener onShowListener) {
        this.onShowListener = onShowListener;
        return this;
    }

    public interface OnClickListener {
        void onClick(Dialog dialog);
    }

    private static final int[] BUTTONS = {
            Dialog.BUTTON_NEGATIVE,
            Dialog.BUTTON_POSITIVE,
            Dialog.BUTTON_NEUTRAL};

}
