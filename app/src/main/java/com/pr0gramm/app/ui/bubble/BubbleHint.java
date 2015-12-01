package com.pr0gramm.app.ui.bubble;

import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.jakewharton.rxbinding.view.RxView;
import com.pr0gramm.app.R;
import com.trello.rxlifecycle.RxLifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;

/**
 * Builds a bubble that is positioned somewhere.
 */
public class BubbleHint {
    private static final Logger logger = LoggerFactory.getLogger("BubbleHint");

    private final View target;
    private final Context context;
    private String text = "no text";
    private int gravity = Gravity.LEFT;
    private ViewGroup root;

    public BubbleHint(View target) {
        this.target = target;
        this.context = target.getContext();

        this.root = rootOf(target);
    }

    public BubbleHint text(String text) {
        this.text = text;
        return this;
    }

    public BubbleHint text(int text) {
        return text(context.getString(text));
    }

    public BubbleHint gravity(int gravity) {
        this.gravity = gravity;
        return this;
    }

    public BubbleHint root(ViewGroup root) {
        this.root = root;
        return this;
    }

    public View show() {
        // inflate view from xml
        BubbleView bubble = (BubbleView) LayoutInflater.from(context).inflate(R.layout.bubble, root, false);
        bubble.setText(text);

        // react to changes to the layout of the root.
        RxView.globalLayouts(root)
                /* add a timer, if we mess up something */
                .mergeWith(Observable
                        .interval(50, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                        .map(ev -> (Void) null))

                /* look for scroll events */
                .mergeWith(parentScrolls(this.target))

                /* calculate new position */
                .map(ev -> targetPosition())
                .startWith(targetPosition())

                /* and update if changed */
                .distinctUntilChanged()
                .compose(RxLifecycle.<Rect>bindView(bubble))
                .subscribe(rect -> {
                    logger.info("view changed to {}", rect);
                    bubble.setTranslationX(rect.left);
                    bubble.setTranslationY(rect.bottom);
                });

        root.addView(bubble);
        return bubble;
    }

    private Rect targetPosition() {
        Rect rect = new Rect(0, 0, target.getWidth(), target.getHeight());
        root.offsetDescendantRectToMyCoords(target, rect);
        return rect;
    }

    private Observable<Void> parentScrolls(View view) {
        Observable<Void> result = Observable.empty();

        ViewParent vp = view.getParent();
        while (vp instanceof View) {
            result = result.mergeWith(RxView.scrollChangeEvents((View) vp).map(v -> (Void) null));
            vp = vp.getParent();
        }

        return result;
    }

    private ViewGroup rootOf(View view) {
        ViewGroup parent = (ViewGroup) view.getParent();
        while(parent.getParent() instanceof ViewGroup)
            parent = (ViewGroup) parent.getParent();

        return parent;
    }
}
