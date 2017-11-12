package com.pr0gramm.app.ui

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.support.annotation.LayoutRes
import android.support.annotation.MainThread
import android.support.annotation.StringRes
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.support.v7.widget.AppCompatCheckBox
import android.text.SpannableString
import android.text.util.Linkify
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.pr0gramm.app.R
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.AndroidUtility.checkMainThread
import com.pr0gramm.app.util.NonCrashingLinkMovementMethod
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

typealias DialogClickListener = (Dialog) -> Unit

typealias DialogOnShowListener = (Dialog) -> Unit

typealias DialogOnCancelListener = (Dialog) -> Unit

/**
 * Helper to build dialogs.
 */
class DialogBuilder internal constructor(private val context: Context) {
    private val builder: AlertDialog.Builder = AlertDialog.Builder(context)

    private val preferences: SharedPreferences = context.getSharedPreferences(
            "dialog-builder-v" + AndroidUtility.buildVersionCode(),
            Context.MODE_PRIVATE)


    private var onClickPositive: DialogClickListener = EMPTY
    private var onClickNegative: DialogClickListener = EMPTY
    private var onClickNeutral: DialogClickListener = EMPTY
    private var onShowListener: DialogOnShowListener = EMPTY
    private var onCancelListener: DialogOnCancelListener = EMPTY

    private var dismissOnClick = true
    private var dontShowAgainKey: String? = null

    private var onNotShown: () -> Unit = {}

    init {
        // default
        builder.setCancelable(false)
    }

    fun content(@StringRes content: Int, vararg args: Any): DialogBuilder {
        return content(getString(content, args))
    }

    fun content(content: CharSequence): DialogBuilder {
        this.builder.setMessage(content)
        return this
    }

    fun contentWithLinks(content: String): DialogBuilder {
        val s = SpannableString(content)
        Linkify.addLinks(s, Linkify.WEB_URLS)
        return content(s)
    }

    fun title(title: String): DialogBuilder {
        builder.setTitle(title)
        return this
    }

    fun title(@StringRes title: Int, vararg args: Any): DialogBuilder {
        return title(getString(title, args))
    }

    fun dontShowAgainKey(key: String): DialogBuilder {
        dontShowAgainKey = key
        return this
    }

    fun onNotShown(fn: () -> Unit): DialogBuilder {
        onNotShown = fn
        return this
    }

    fun layout(@LayoutRes view: Int): DialogBuilder {
        builder.setView(view)
        return this
    }

    fun noAutoDismiss(): DialogBuilder {
        this.dismissOnClick = false
        return this
    }

    // TODO remove after kotlin migration.
    fun positive(@StringRes text: Int, onClick: Runnable): DialogBuilder {
        return positive(getString(text), { onClick.run() })
    }

    @JvmOverloads
    fun positive(@StringRes text: Int, onClick: DialogClickListener = EMPTY): DialogBuilder {
        return positive(getString(text), onClick)
    }

    @JvmOverloads
    fun positive(text: String = getString(R.string.okay), onClick: DialogClickListener = EMPTY): DialogBuilder {
        builder.setPositiveButton(text, null)
        this.onClickPositive = onClick
        return this
    }

    @JvmOverloads
    fun negative(@StringRes text: Int = R.string.cancel, onClick: DialogClickListener = EMPTY): DialogBuilder {
        return negative(getString(text), onClick)
    }

    fun negative(text: String, onClick: DialogClickListener): DialogBuilder {
        builder.setNegativeButton(text, null)
        this.onClickNegative = onClick
        return this
    }

    @JvmOverloads
    fun neutral(@StringRes text: Int, onClick: DialogClickListener = EMPTY): DialogBuilder {
        return neutral(getString(text), onClick)
    }

    @JvmOverloads
    fun neutral(text: String = getString(R.string.okay), onClick: DialogClickListener = EMPTY): DialogBuilder {
        builder.setNeutralButton(text, null)
        this.onClickNeutral = onClick
        return this
    }


    private fun getString(@StringRes content: Int): String {
        return context.getString(content)
    }

    private fun getString(@StringRes content: Int, args: Array<out Any>): String {
        return context.getString(content, *args)
    }

    fun show(): Dialog {
        val dialog = build()

        if (shouldNotShowDialog()) {
            onNotShown()
        } else {
            dialog.show()
        }

        return dialog
    }

    fun build(): Dialog {
        if (shouldNotShowDialog()) {
            logger.info("Not showing dialog '{}'.", dontShowAgainKey)

            // return a dialog that closes itself whens shown.
            val dialog = Dialog(context)
            dialog.setOnShowListener {
                it.dismiss()
                onNotShown()
            }

            return dialog
        }

        val dialog = builder.create()

        dialog.setOnShowListener {
            val messageView: View? = dialog.findViewById(android.R.id.message)

            val dontShowAgainClicked = setupDontShowAgainView(messageView)

            for (button in BUTTONS) {
                val btn = dialog.getButton(button)
                btn?.setOnClickListener {
                    onButtonClicked(button, dialog, dontShowAgainClicked.get())
                }
            }

            if (messageView is TextView) {
                messageView.movementMethod = NonCrashingLinkMovementMethod()
            }

            onShowListener(dialog)
        }

        if (onCancelListener !== EMPTY) {
            dialog.setOnCancelListener {
                onCancelListener(dialog)
            }
        }

        return dialog
    }

    private fun setupDontShowAgainView(messageView: View?): AtomicBoolean {
        val dontShowAgainClicked = AtomicBoolean()

        if (messageView != null && dontShowAgainKey != null) {
            val parent = messageView.parent
            if (parent is LinearLayout) {
                val checkbox = AppCompatCheckBox(messageView.context)
                checkbox.setText(R.string.dialog_dont_show_again)

                val params = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT)

                params.leftMargin = messageView.paddingLeft
                params.rightMargin = messageView.paddingRight
                params.topMargin = params.leftMargin / 2
                checkbox.layoutParams = params

                // remember changes here.
                checkbox.setOnCheckedChangeListener { _, checked ->
                    dontShowAgainClicked.set(checked)
                }

                parent.addView(checkbox)
            }
        }

        return dontShowAgainClicked
    }

    private fun shouldNotShowDialog(): Boolean {
        return dontShowAgainKey != null && this.preferences.getBoolean(dontShowAgainKey, false)
    }

    private fun onButtonClicked(button: Int, dialog: AlertDialog, dontShowAgain: Boolean) {
        when (button) {
            Dialog.BUTTON_POSITIVE -> onClickPositive(dialog)
            Dialog.BUTTON_NEGATIVE -> onClickNegative(dialog)
            Dialog.BUTTON_NEUTRAL -> onClickNeutral(dialog)
        }

        if (dismissOnClick) {
            dialog.dismiss()
        }

        if (dontShowAgainKey != null && dontShowAgain) {
            logger.info("Never show dialog '{}' again", dontShowAgainKey)
            preferences.edit()
                    .putBoolean(dontShowAgainKey, true)
                    .apply()
        }
    }

    fun cancelable(): DialogBuilder {
        builder.setCancelable(true)
        return this
    }

    fun onShow(onShowListener: DialogOnShowListener): DialogBuilder {
        this.onShowListener = onShowListener
        return this
    }

    fun onCancel(onCancelListener: DialogOnCancelListener): DialogBuilder {
        this.onCancelListener = onCancelListener
        return this
    }

    companion object {
        private val logger = LoggerFactory.getLogger("DialogBuilder")
        private val BUTTONS = intArrayOf(Dialog.BUTTON_NEGATIVE, Dialog.BUTTON_POSITIVE, Dialog.BUTTON_NEUTRAL)
        private val EMPTY: DialogClickListener = {}

        @MainThread
        fun start(context: Context): DialogBuilder {
            checkMainThread()
            return DialogBuilder(context)
        }
    }
}

inline fun dialog(fragment: Fragment, configure: DialogBuilder.() -> Unit): Dialog {
    return DialogBuilder.start(fragment.context!!).apply { configure() }.build()
}

inline fun dialog(context: Context, configure: DialogBuilder.() -> Unit): Dialog {
    return DialogBuilder.start(context).apply { configure() }.build()
}

inline fun showDialog(fragment: Fragment, configure: DialogBuilder.() -> Unit): Dialog {
    return DialogBuilder.start(fragment.context!!).apply { configure() }.show()
}
inline fun showDialog(context: Context, configure: DialogBuilder.() -> Unit): Dialog {
    return DialogBuilder.start(context).apply { configure() }.show()
}
