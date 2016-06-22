package com.pr0gramm.app.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.support.annotation.LayoutRes;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatCheckBox;
import android.text.SpannableString;
import android.text.method.MovementMethod;
import android.text.util.Linkify;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.pr0gramm.app.R;
import com.pr0gramm.app.util.AndroidUtility;
import com.pr0gramm.app.util.NonCrashingLinkMovementMethod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.pr0gramm.app.util.AndroidUtility.checkMainThread;

/**
 * Helper to build dialogs.
 */
@ParametersAreNonnullByDefault
public class DialogBuilder {
    private final Context context;
    private final AlertDialog.Builder builder;
    private final SharedPreferences preferences;

    private boolean autoDismiss = true;
    private OnClickListener positiveOnClick = DO_NOTHING;
    private OnClickListener negativeOnClick = DO_NOTHING;
    private OnClickListener neutralOnClick = DO_NOTHING;
    private DialogInterface.OnShowListener onShowListener;
    private DialogInterface.OnCancelListener onCancelListener;
    private MovementMethod movementMethod;

    @Nullable
    private String dontShowAgainKey;

    private DialogBuilder(Context context) {
        this.context = context;
        this.builder = new AlertDialog.Builder(context);

        this.preferences = context.getSharedPreferences(
                "dialog-builder-v" + AndroidUtility.buildVersionCode(),
                Context.MODE_PRIVATE);

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

    public DialogBuilder dontShowAgainKey(@Nullable String key) {
        dontShowAgainKey = key;
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

        if (shouldNotShowDialog()) {
            dialog.show();
        }

        return dialog;
    }

    private static final Logger logger = LoggerFactory.getLogger("DialogBuilder");

    public Dialog build() {
        if (shouldNotShowDialog()) {
            logger.info("Dont show dialog '{}'.", dontShowAgainKey);

            // return a dialog that closes itself whens shown.
            Dialog dialog = new Dialog(context);
            dialog.setOnShowListener(DialogInterface::dismiss);
            return dialog;
        }

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(di -> {
            View messageView = dialog.findViewById(android.R.id.message);

            AtomicBoolean dontShowAgainClicked = setupDontShowAgainView(messageView);

            for (int button : BUTTONS) {
                Button btn = dialog.getButton(button);
                if (btn != null) {
                    btn.setOnClickListener(v -> onButtonClicked(button, dialog, dontShowAgainClicked.get()));
                }
            }

            if (movementMethod != null) {
                if (messageView instanceof TextView) {
                    ((TextView) messageView).setMovementMethod(movementMethod);
                }
            }

            if (onShowListener != null)
                onShowListener.onShow(dialog);
        });

        if (onCancelListener != null)
            dialog.setOnCancelListener(onCancelListener);

        return dialog;
    }

    private AtomicBoolean setupDontShowAgainView(@Nullable View messageView) {
        AtomicBoolean dontShowAgainClicked = new AtomicBoolean();

        if (messageView != null && dontShowAgainKey != null) {
            ViewParent parent = messageView.getParent();
            if (parent instanceof LinearLayout) {
                CheckBox checkbox = new AppCompatCheckBox(messageView.getContext());
                checkbox.setText(R.string.dialog_dont_show_again);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);

                params.leftMargin = messageView.getPaddingLeft();
                params.rightMargin = messageView.getPaddingRight();
                params.topMargin = params.leftMargin / 2;
                checkbox.setLayoutParams(params);

                // remember changes here.
                checkbox.setOnCheckedChangeListener((v, checked) -> dontShowAgainClicked.set(checked));

                LinearLayout linearLayout = (LinearLayout) parent;
                linearLayout.addView(checkbox);
            }
        }

        return dontShowAgainClicked;
    }

    private boolean shouldNotShowDialog() {
        return dontShowAgainKey != null && this.preferences.getBoolean(dontShowAgainKey, false);
    }

    private void onButtonClicked(int button, AlertDialog dialog, boolean dontShowAgain) {
        if (button == Dialog.BUTTON_POSITIVE)
            positiveOnClick.onClick(dialog);

        if (button == Dialog.BUTTON_NEGATIVE)
            negativeOnClick.onClick(dialog);

        if (button == Dialog.BUTTON_NEUTRAL)
            neutralOnClick.onClick(dialog);

        if (autoDismiss)
            dialog.dismiss();

        if (dontShowAgainKey != null && dontShowAgain) {
            logger.info("Never show dialog '{}' again", dontShowAgainKey);
            preferences.edit()
                    .putBoolean(dontShowAgainKey, true)
                    .apply();
        }
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
