package com.pr0gramm.app.ui

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.text.SpannableString
import android.text.util.Linkify
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.content.edit
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.NonCrashingLinkMovementMethod
import com.pr0gramm.app.util.catchAll
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.inflate
import com.pr0gramm.app.util.layoutInflater
import java.util.concurrent.atomic.AtomicBoolean

typealias DialogClickListener = (Dialog) -> Unit

typealias DialogOnShowListener = (Dialog) -> Unit

typealias DialogOnCancelListener = (Dialog) -> Unit

/**
 * Helper to build dialogs.
 */
class DialogBuilder(private val context: Context, private val bottomSheet: Boolean = false) {
    private val dialogButtons = intArrayOf(Dialog.BUTTON_NEGATIVE, Dialog.BUTTON_POSITIVE, Dialog.BUTTON_NEUTRAL)

    private val preferences: SharedPreferences by lazy {
        context.applicationContext.getSharedPreferences(
                "dialog-builder-v" + AndroidUtility.buildVersionCode(),
                Context.MODE_PRIVATE)
    }

    private val logger = Logger("DialogBuilder")

    private var buttonPositiveText: String? = null
    private var buttonPositiveClick: DialogClickListener? = null

    private var buttonNegativeText: String? = null
    private var buttonNegativeClick: DialogClickListener? = null

    private var buttonNeutralText: String? = null
    private var buttonNeutralClick: DialogClickListener? = null

    private var onShowListener: DialogOnShowListener? = null
    private var onCancelListener: DialogOnCancelListener? = null

    private var dismissOnClick = true
    private var dontShowAgainKey: String? = null

    private var cancelable = false

    private var title: CharSequence? = null
    private var contentText: CharSequence? = null
    private var contentViewId: Int? = null
    private var contentView: View? = null

    private var onNotShown: () -> Unit = {}

    fun content(@StringRes content: Int, vararg args: Any) {
        return content(getString(content, args))
    }

    fun content(content: CharSequence) {
        contentText = content
        contentView = null
        contentViewId = null
    }

    fun contentWithLinks(content: String) {
        val s = SpannableString(content)
        Linkify.addLinks(s, Linkify.WEB_URLS)
        return content(s)
    }

    fun title(title: String) {
        this.title = title
    }

    fun title(@StringRes title: Int, vararg args: Any) {
        return title(getString(title, args))
    }

    fun dontShowAgainKey(key: String) {
        dontShowAgainKey = key
    }

    fun onNotShown(fn: () -> Unit) {
        onNotShown = fn
    }

    fun layout(@LayoutRes view: Int) {
        contentViewId = view
        contentView = null
        contentText = null
    }

    fun contentView(view: View) {
        contentViewId = null
        contentView = view
        contentText = null
    }

    fun noAutoDismiss() {
        this.dismissOnClick = false
    }

    fun positive(@StringRes text: Int, onClick: DialogClickListener? = null) {
        return positive(getString(text), onClick)
    }

    fun positive(text: String = getString(R.string.okay), onClick: DialogClickListener? = null) {
        buttonPositiveText = text
        buttonPositiveClick = onClick
    }

    fun negative(@StringRes text: Int = R.string.cancel, onClick: DialogClickListener? = null) {
        return negative(getString(text), onClick)
    }

    fun negative(text: String, onClick: DialogClickListener? = null) {
        buttonNegativeText = text
        buttonNegativeClick = onClick
    }

    fun neutral(@StringRes text: Int, onClick: DialogClickListener? = null) {
        return neutral(getString(text), onClick)
    }

    fun neutral(text: String = getString(R.string.okay), onClick: DialogClickListener? = null) {
        buttonNeutralText = text
        buttonNeutralClick = onClick
    }

    fun cancelable() {
        cancelable = true
    }

    fun onShow(onShowListener: DialogOnShowListener) {
        this.onShowListener = onShowListener
    }

    fun onCancel(onCancelListener: DialogOnCancelListener) {
        this.onCancelListener = onCancelListener
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
            logger.info { "Not showing dialog '$dontShowAgainKey'." }

            // return a dialog that closes itself whens shown.
            val dialog = Dialog(context)
            dialog.setOnShowListener {
                it.dismiss()
                onNotShown()
            }

            return dialog
        }

        val dialog: Dialog = if (bottomSheet) {
            BottomSheetAlertDialog(context).let { b ->
                b.setCancelable(cancelable)

                buttonPositiveText?.let { b.setPositiveButton(it) }
                buttonNegativeText?.let { b.setNegativeButton(it) }
                buttonNeutralText?.let { b.setNeutralButton(it) }

                title?.let { b.setTitle(it) }

                when {
                    contentText != null -> b.setTextContent(contentText!!)
                    contentView != null -> b.setCustomContent(contentView!!)
                    contentViewId != null -> b.setCustomContent(contentViewId!!)
                }

                // only one of those two is non zero.
                contentText?.let { b.setTextContent(it) }
                contentViewId?.let { b.setCustomContent(it) }

                b
            }
        } else {
            AlertDialog.Builder(context).let { b ->
                b.setCancelable(cancelable)

                buttonPositiveText?.let { b.setPositiveButton(it, null) }
                buttonNegativeText?.let { b.setNegativeButton(it, null) }
                buttonNeutralText?.let { b.setNeutralButton(it, null) }

                title?.let { b.setTitle(it) }

                when {
                    contentText != null -> b.setMessage(contentText)
                    contentView != null -> b.setView(contentView)
                    contentViewId != null -> b.setView(contentViewId!!)
                }

                b.create()
            }
        }

        dialog.setOnShowListener {
            val messageView: View? = dialog.findViewById(android.R.id.message)

            if (messageView is TextView) {
                messageView.movementMethod = NonCrashingLinkMovementMethod
            }

            val dontShowAgainClicked = setupDontShowAgainView(messageView)

            // we handle the clicks to the buttons.
            for (button in dialogButtons) {
                val buttonView = when (dialog) {
                    is AlertDialog -> dialog.getButton(button)
                    is BottomSheetAlertDialog -> dialog.getButton(button)
                    else -> null
                }

                buttonView?.setOnClickListener {
                    onButtonClicked(button, dialog, dontShowAgainClicked.get())
                }
            }

            if (dialog is BottomSheetDialog) {
                val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                if (bottomSheet is FrameLayout) {
                    catchAll {
                        BottomSheetBehavior
                                .from(bottomSheet)
                                .setState(BottomSheetBehavior.STATE_EXPANDED)
                    }
                }
            }

            if (cancelable) {
                onCancelListener?.let { cl ->
                    dialog.setOnCancelListener { cl(dialog) }
                }
            }

            onShowListener?.invoke(dialog)
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

    private fun onButtonClicked(button: Int, dialog: Dialog, dontShowAgain: Boolean) {
        when (button) {
            Dialog.BUTTON_POSITIVE -> buttonPositiveClick?.invoke(dialog)
            Dialog.BUTTON_NEGATIVE -> buttonNegativeClick?.invoke(dialog)
            Dialog.BUTTON_NEUTRAL -> buttonNeutralClick?.invoke(dialog)
        }

        if (dismissOnClick) {
            dialog.dismiss()
        }

        dontShowAgainKey.takeIf { dontShowAgain }?.let { key ->
            logger.info { "Never show dialog '$key' again" }
            preferences.edit {
                putBoolean(key, true)
            }
        }
    }
}

fun resolveDialogTheme(context: Context, @StyleRes resid: Int): Int {
    // Check to see if this resourceId has a valid package ID.
    return if (resid.ushr(24) and 0x000000ff >= 0x00000001) {   // start of real resource IDs.
        resid
    } else {
        val outValue = TypedValue()
        context.theme.resolveAttribute(androidx.appcompat.R.attr.alertDialogTheme, outValue, true)
        outValue.resourceId
    }
}

private class BottomSheetAlertDialog(ctx: Context, theme: Int = R.style.MyBottomSheetDialog) :
        BottomSheetDialog(ContextThemeWrapper(ctx, resolveDialogTheme(ctx, theme)), resolveDialogTheme(ctx, theme)) {

    private val view: ViewGroup = (ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
            .inflate(R.layout.bottom_sheet_alert_dialog)
            .also { setContentView(it) } as ViewGroup

    val buttonPositive: Button = view.find(android.R.id.button1)
    val buttonNegative: Button = view.find(android.R.id.button2)
    val buttonNeutral: Button = view.find(android.R.id.button3)

    private val titleSpacerNoTitle: View = view.find(R.id.titleSpacerNoTitle)
    private val textContent: TextView = view.find(R.id.textContent)
    private val customContent: ViewGroup = view.find(R.id.custom)
    private val title: TextView = view.find(R.id.title)

    fun setCustomContent(@LayoutRes content: Int) {
        customContent.removeAllViews()
        customContent.layoutInflater.inflate(content, customContent, true)
    }

    fun setCustomContent(@LayoutRes view: View) {
        customContent.removeAllViews()
        customContent.addView(view)
    }

    fun setTextContent(text: CharSequence) {
        textContent.text = text
    }

    override fun setTitle(text: CharSequence?) {
        super.setTitle(text)

        title.text = text
        title.isVisible = true
        titleSpacerNoTitle.isVisible = false
    }

    fun setPositiveButton(text: String) {
        buttonPositive.isVisible = true
        buttonPositive.text = text
    }

    fun setNegativeButton(text: String) {
        buttonNegative.isVisible = true
        buttonNegative.text = text
    }

    fun setNeutralButton(text: String) {
        buttonNeutral.isVisible = true
        buttonNeutral.text = text
    }

    fun getButton(idx: Int): Button? {
        return when (idx) {
            AlertDialog.BUTTON_POSITIVE -> buttonPositive
            AlertDialog.BUTTON_NEGATIVE -> buttonNegative
            AlertDialog.BUTTON_NEUTRAL -> buttonNeutral
            else -> null
        }
    }
}

inline fun dialog(fragment: androidx.fragment.app.Fragment, configure: DialogBuilder.() -> Unit): Dialog {
    return dialog(fragment.requireContext(), configure)
}

inline fun dialog(context: Context, configure: DialogBuilder.() -> Unit): Dialog {
    return DialogBuilder(context).apply(configure).build()
}

inline fun bottomSheet(fragment: androidx.fragment.app.Fragment, configure: DialogBuilder.() -> Unit): Dialog {
    return bottomSheet(fragment.requireContext(), configure)
}

inline fun bottomSheet(context: Context, configure: DialogBuilder.() -> Unit): Dialog {
    return DialogBuilder(context, bottomSheet = true).apply(configure).build()
}

inline fun showDialog(fragment: androidx.fragment.app.Fragment, configure: DialogBuilder.() -> Unit): Dialog {
    return showDialog(fragment.requireContext(), configure)
}

inline fun showDialog(context: Context, configure: DialogBuilder.() -> Unit): Dialog {
    return DialogBuilder(context).apply(configure).show()
}

inline fun showBottomSheet(fragment: androidx.fragment.app.Fragment, configure: DialogBuilder.() -> Unit): Dialog {
    return showBottomSheet(fragment.requireContext(), configure)
}

inline fun showBottomSheet(context: Context, configure: DialogBuilder.() -> Unit): Dialog {
    return DialogBuilder(context, bottomSheet = true).apply(configure).show()
}
