package com.pr0gramm.app.ui

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_INCLUSIVE_EXCLUSIVE
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import java.util.*

/**
 * A [SpannableStringBuilder] wrapper whose API doesn't make me want to stab my eyes out.
 * See https://gist.github.com/JakeWharton/11274467
 */
class Truss {
    private val builder: SpannableStringBuilder = SpannableStringBuilder()
    private val stack: Deque<Span> = ArrayDeque<Span>()

    fun append(charSequence: CharSequence): Truss {
        builder.append(charSequence)
        return this
    }

    fun append(charSequence: CharSequence, vararg spans: Any): Truss {
        for (span in spans) {
            pushSpan(span)
        }

        builder.append(charSequence)

        for (span in spans) {
            popSpan()
        }

        return this
    }

    /**
     * Starts `span` at the current position in the builder.
     */
    fun pushSpan(span: Any): Truss {
        stack.addLast(Span(builder.length, span))
        return this
    }

    /**
     * End the most recently pushed span at the current position in the builder.
     */
    fun popSpan(): Truss {
        val span = stack.removeLast()
        builder.setSpan(span.span, span.start, builder.length, SPAN_INCLUSIVE_EXCLUSIVE)
        return this
    }

    /**
     * Create the final [CharSequence], popping any remaining spans.
     */
    fun build(): CharSequence {
        while (!stack.isEmpty()) {
            popSpan()
        }
        return builder
    }

    private class Span(val start: Int, val span: Any)

    companion object {
        val bold = StyleSpan(Typeface.BOLD)
        val italic = StyleSpan(Typeface.ITALIC)
        val larger = RelativeSizeSpan(1.2f)
    }
}