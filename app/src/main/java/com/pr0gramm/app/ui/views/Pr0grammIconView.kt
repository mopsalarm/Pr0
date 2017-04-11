package com.pr0gramm.app.ui.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.BoringLayout
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue.applyDimension
import android.view.View
import com.google.common.base.MoreObjects.firstNonNull
import com.pr0gramm.app.R
import com.pr0gramm.app.util.cached
import com.pr0gramm.app.util.observeChange
import com.pr0gramm.app.util.use

/**
 * Text view with a custom font.
 */
class Pr0grammIconView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var textColor by observeChange(Color.RED) {
        invalidate()
    }

    var textSize by observeChange(16f) {
        invalidate()
        requestLayout()
    }

    var text by observeChange("+") {
        invalidate()
        requestLayout()
    }

    private val cachedLayout = cached { buildLayout() }

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.IconView, 0, 0).use { a ->
            text = firstNonNull(a.getString(R.styleable.IconView_iv_text), "")
            textSize = a.getDimensionPixelSize(R.styleable.IconView_iv_textSize, 16).toFloat()
            textColor = a.getColor(R.styleable.IconView_iv_textColor, Color.RED)
        }
    }

    private fun buildLayout(): Layout {
        val paint = TextPaint()
        paint.color = textColor
        paint.textSize = textSize
        paint.isAntiAlias = true
        paint.typeface = getFontfaceInstance(this)

        val metrics = BoringLayout.isBoring(text, paint)
        if (metrics != null) {
            return BoringLayout(text, paint, metrics.width,
                    Layout.Alignment.ALIGN_NORMAL, 1f, 0f, metrics, true)
        } else {
            return StaticLayout(text, paint, 0,
                    Layout.Alignment.ALIGN_NORMAL, 1f, 0f, true)
        }
    }

    override fun invalidate() {
        cachedLayout.invalidate()
        super.invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        cachedLayout.value.draw(canvas)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(cachedLayout.value.width, cachedLayout.value.height)
    }

    fun setTextSize(unit: Int, textSize: Int) {
        this.textSize = applyDimension(unit, textSize.toFloat(), resources.displayMetrics)
    }

    fun setTextColor(textColor: ColorStateList) {
        this.textColor = textColor.defaultColor
    }

    companion object {
        private var TYPEFACE: Typeface? = null

        /**
         * Loads the pr0gramm icon typeface. The font is only loaded once. This method
         * will then return the same instance on every further call.

         * @param view A view that will be used to get the context and to check if we
         * *             are in edit mode or not.
         */
        private fun getFontfaceInstance(view: View): Typeface {
            if (TYPEFACE == null) {
                if (view.isInEditMode) {
                    TYPEFACE = Typeface.DEFAULT
                } else {
                    val assets = view.context.assets
                    TYPEFACE = Typeface.createFromAsset(assets, "fonts/pict0gramm-v3.ttf")
                }
            }

            return TYPEFACE!!
        }
    }
}
