package com.pr0gramm.app.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.LayoutRes;
import android.support.annotation.MainThread;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.text.SpannableString;
import android.text.method.MovementMethod;
import android.text.util.Linkify;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.pr0gramm.app.R;
import com.pr0gramm.app.util.NonCrashingLinkMovementMethod;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.pr0gramm.app.util.AndroidUtility.checkMainThread;

/**
 * Helper to build dialogs.
 */
@ParametersAreNonnullByDefault
public class DialogBuilder {
    private final Context context;
    private final AlertDialog.Builder builder;

    private boolean autoDismiss = true;
    private OnClickListener positiveOnClick = DO_NOTHING;
    private OnClickListener negativeOnClick = DO_NOTHING;
    private OnClickListener neutralOnClick = DO_NOTHING;
    private DialogInterface.OnShowListener onShowListener;
    private DialogInterface.OnCancelListener onCancelListener;
    private MovementMethod movementMethod;

    private DialogBuilder(Context context) {
        // this.theme = ThemeHelper.theme().popup;
        this.context = context;
        this.builder = new AlertDialog.Builder(context);

        // default
        builder.setCancelable(false);
    }

    public DialogBuilder content(CharSequence content) {
        this.builder.setMessage(content);
        return this;
    }

    public DialogBuilder contentWithLinks(String content) {
        SpannableString s = new SpannableString(content);
        Linkify.addLinks(s, Linkify.WEB_URLS);

        movementMethod = NonCrashingLinkMovementMethod.getInstance();

        return content(s);
    }

    public DialogBuilder content(@StringRes int content, Object... args) {
        return content(getString(content, args));
    }

    public DialogBuilder title(String title) {
        builder.setTitle(title);
        return this;
    }

    public DialogBuilder title(@StringRes int title, Object... args) {
        return title(getString(title, args));
    }

    public DialogBuilder noAutoDismiss() {
        autoDismiss = false;
        return this;
    }

    public DialogBuilder layout(@LayoutRes int view) {
        builder.setView(view);
        return this;
    }

    public DialogBuilder positive() {
        return positive(getString(R.string.okay));
    }

    public DialogBuilder positive(@StringRes int text, OnClickListener onClick) {
        return positive(getString(text), onClick);
    }

    public DialogBuilder positive(@StringRes int text, Runnable onClick) {
        return positive(getString(text), dialog -> onClick.run());
    }

    public DialogBuilder positive(String text) {
        builder.setPositiveButton(text, null);
        return this;
    }

    public DialogBuilder positive(String text, OnClickListener onClick) {
        builder.setPositiveButton(text, null);
        this.positiveOnClick = onClick;
        return this;
    }

    public DialogBuilder negative(@StringRes int text) {
        return negative(getString(text));
    }

    public DialogBuilder negative(OnClickListener onClick) {
        return negative(getString(R.string.cancel), onClick);
    }

    public DialogBuilder negative(@StringRes int text, Runnable onClick) {
        return negative(getString(text), dialog -> onClick.run());
    }

    public DialogBuilder negative(String text) {
        builder.setNegativeButton(text, null);
        return this;
    }

    public DialogBuilder negative(String text, OnClickListener onClick) {
        builder.setNegativeButton(text, null);
        this.negativeOnClick = onClick;
        return this;
    }

    public DialogBuilder neutral(@StringRes int text) {
        return neutral(getString(text));
    }

    public DialogBuilder neutral(@StringRes int text, OnClickListener onClick) {
        return neutral(getString(text), onClick);
    }

    public DialogBuilder neutral(@StringRes int text, Runnable onClick) {
        return neutral(getString(text), dialog -> onClick.run());
    }

    public DialogBuilder neutral(String text) {
        builder.setNeutralButton(text, null);
        return this;
    }

    public DialogBuilder neutral(String text, OnClickListener onClick) {
        builder.setNeutralButton(text, null);
        this.neutralOnClick = onClick;
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
        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(di -> {
            for (int button : BUTTONS) {
                Button btn = dialog.getButton(button);
                if (btn != null) {
                    btn.setOnClickListener(v -> onButtonClicked(button, dialog));
                }
            }

            if (movementMethod != null) {
                View view = dialog.findViewById(android.R.id.message);
                if (view instanceof TextView) {
                    ((TextView) view).setMovementMethod(movementMethod);
                }
            }

            if (onShowListener != null)
                onShowListener.onShow(dialog);
        });

        if (onCancelListener != null)
            dialog.setOnCancelListener(onCancelListener);

        return dialog;
    }

    private void onButtonClicked(int button, AlertDialog dialog) {
        if (button == Dialog.BUTTON_POSITIVE)
            positiveOnClick.onClick(dialog);

        if (button == Dialog.BUTTON_NEGATIVE)
            negativeOnClick.onClick(dialog);

        if (button == Dialog.BUTTON_NEUTRAL)
            neutralOnClick.onClick(dialog);

        if (autoDismiss)
            dialog.dismiss();
    }

    @MainThread
    public static DialogBuilder start(Context context) {
        checkMainThread();
        return new DialogBuilder(context);
    }

    public DialogBuilder cancelable() {
        builder.setCancelable(true);
        return this;
    }

    public DialogBuilder onShow(Dialog.OnShowListener onShowListener) {
        this.onShowListener = onShowListener;
        return this;
    }

    public DialogBuilder onCancel(Dialog.OnCancelListener onCancelListener) {
        this.onCancelListener = onCancelListener;
        return this;
    }

    public interface OnClickListener {
        void onClick(Dialog dialog);
    }

    private static final int[] BUTTONS = {
            Dialog.BUTTON_NEGATIVE,
            Dialog.BUTTON_POSITIVE,
            Dialog.BUTTON_NEUTRAL};

    private static final OnClickListener DO_NOTHING = dialog -> {
    };
}
