package com.pr0gramm.app.ui;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import java.util.ArrayDeque;
import java.util.Deque;

import static android.text.Spanned.SPAN_INCLUSIVE_EXCLUSIVE;

/**
 * A {@link SpannableStringBuilder} wrapper whose API doesn't make me want to stab my eyes out.
 * See https://gist.github.com/JakeWharton/11274467
 */
public class Truss {
    private final SpannableStringBuilder builder;
    private final Deque<Span> stack;

    public Truss() {
        builder = new SpannableStringBuilder();
        stack = new ArrayDeque<>();
    }

    public Truss append(CharSequence charSequence) {
        builder.append(charSequence);
        return this;
    }

    public Truss append(CharSequence charSequence, Object... spans) {
        for (Object span : spans) {
            pushSpan(span);
        }

        builder.append(charSequence);

        for (Object span : spans) {
            popSpan();
        }

        return this;
    }

    /**
     * Starts {@code span} at the current position in the builder.
     */
    public Truss pushSpan(Object span) {
        stack.addLast(new Span(builder.length(), span));
        return this;
    }

    /**
     * End the most recently pushed span at the current position in the builder.
     */
    public Truss popSpan() {
        Span span = stack.removeLast();
        builder.setSpan(span.span, span.start, builder.length(), SPAN_INCLUSIVE_EXCLUSIVE);
        return this;
    }

    /**
     * Create the final {@link CharSequence}, popping any remaining spans.
     */
    public CharSequence build() {
        while (!stack.isEmpty()) {
            popSpan();
        }
        return builder; // TODO make immutable copy?
    }

    private static final class Span {
        final int start;
        final Object span;

        public Span(int start, Object span) {
            this.start = start;
            this.span = span;
        }
    }

    public static StyleSpan bold() {
        return new StyleSpan(Typeface.BOLD);
    }

    public static StyleSpan italic() {
        return new StyleSpan(Typeface.ITALIC);
    }

    public static RelativeSizeSpan larger() {
        return new RelativeSizeSpan(1.2f);
    }
}