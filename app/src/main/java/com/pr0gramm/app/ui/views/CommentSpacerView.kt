package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import com.pr0gramm.app.Logger
import com.pr0gramm.app.Settings
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.time
import com.pr0gramm.app.ui.Themes
import com.pr0gramm.app.ui.fragments.post.Spacings
import com.pr0gramm.app.ui.paint
import com.pr0gramm.app.util.dp
import com.pr0gramm.app.util.getColorCompat
import com.pr0gramm.app.util.memorize
import com.pr0gramm.app.util.observeChangeEx
import kotlin.math.pow
import kotlin.math.roundToInt


/**
 */
class CommentSpacerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleRes: Int = 0) : ConstraintLayout(context, attrs, defStyleRes) {
    private val basePaddingLeft = paddingLeft

    private val lineWidth = context.dp(1f)
    private val lineMargin = context.dp(8f)

    private val logger = Logger("CommentSpacerView")

    private val paint by lazy(LazyThreadSafetyMode.NONE) {
        paint {
            isAntiAlias = true
            style = Paint.Style.STROKE
            color = initialColor(context)
            strokeWidth = context.dp(1f)
        }
    }

    var depth: Int by observeChangeEx(-1) { oldValue, newValue ->
        if (oldValue != newValue) {
            val paddingLeft = spaceAtDepth(newValue).toInt()
            setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
            requestLayout()
        }
    }

    var spacings: Spacings by observeChangeEx(Spacings(0)) { oldValue, newValue ->
        if (oldValue != newValue) {
            invalidate()
        }
    }

    init {
        // we need to overwrite the default of the view group.
        setWillNotDraw(false)

        if (isInEditMode) {
            depth = 5
            spacings = Spacings(5)
        }
    }

    private fun spaceAtDepth(depth: Int): Float {
        return basePaddingLeft + lineMargin * depth.toDouble().pow(1 / 1.2).toFloat()
    }

    private fun lineX(depth: Int): Float {
        return spaceAtDepth(depth)
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) = logger.time("Draw spacer at depth $depth") {
        super.onDraw(canvas)

        if (spacings.maxLineIsAt < 2)
            return

        val colorful = Settings.get().colorfulCommentLines

        val height = height.toFloat()

        // height of the little connector thing
        val connectHeight = context.dp(12f)

        val lines = spacings.lines(from = 2)

        paint.color = initialColor(context)

        for ((idx, line) in lines.withIndex()) {
            val x = (lineX(line) - lineWidth).roundToInt().toFloat()

            if (colorful) {
                // set the color for the next line
                paint.color = colorValue(context, line)
            }


            if (line < 3 || idx < lines.lastIndex || !spacings.isFirstChild) {
                paint.shader = null

                canvas.drawLine(x, 0f, x, height, paint)

            } else {
                if (colorful) {
                    paint.shader = LinearGradient(
                            0f, 0f, 0f, connectHeight,
                            colorValue(context, line - 1),
                            colorValue(context, line),
                            Shader.TileMode.CLAMP)
                }

                // x of the previous line. We use this to connect using the connector
                val previousX = (lineX(line - 1) - lineWidth).roundToInt().toFloat()

                val path = Path().apply {
                    moveTo(previousX, 0f)
                    cubicTo(previousX, connectHeight * 0.5f, x, connectHeight * 0.5f, x, connectHeight)
                    lineTo(x, height)
                }

                canvas.drawPath(path, paint)
            }
        }

    }

    companion object {
        private val initialColor by memorize<Context, Int> { context ->
            context.getColorCompat(com.pr0gramm.app.R.color.comment_line)
        }

        private val cachedColorValues by memorize<Context, IntArray> { context ->
            // themes we want to use
            val themes = listOf(
                    Themes.ORANGE, Themes.BLUE, Themes.OLIVE, Themes.PINK, Themes.GREEN,
                    Themes.ORANGE, Themes.BLUE, Themes.OLIVE, Themes.PINK, Themes.GREEN)

            // start at our currently configured theme (if it is in the list of themes)
            val themeSelection = if (ThemeHelper.theme in themes) {
                themes.dropWhile { it !== ThemeHelper.theme }
            } else {
                themes
            }

            // get a list of the accent colors
            val colors = listOf(initialColor(context)) + themeSelection.take(5).map { theme ->
                blendColors(0.3f, initialColor(context), context.getColorCompat(theme.accentColor))
            }

            colors.toIntArray()
        }

        private fun colorValue(context: Context, depth: Int): Int {
            if (depth < 3) {
                return initialColor(context)
            }

            val colorValues = cachedColorValues(context)
            return colorValues[(depth - 3) % colorValues.size]
        }
    }
}

fun blendColors(factor: Float, source: Int, target: Int): Int {
    val f = factor.coerceIn(0f, 1f)

    val sa = Color.alpha(source)
    val sr = Color.red(source)
    val sg = Color.green(source)
    val sb = Color.blue(source)

    val ta = Color.alpha(target)
    val tr = Color.red(target)
    val tg = Color.green(target)
    val tb = Color.blue(target)

    val a = (sa + f * (ta - sa)).roundToInt() and 0xff
    val r = (sr + f * (tr - sr)).roundToInt() and 0xff
    val g = (sg + f * (tg - sg)).roundToInt() and 0xff
    val b = (sb + f * (tb - sb)).roundToInt() and 0xff

    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
