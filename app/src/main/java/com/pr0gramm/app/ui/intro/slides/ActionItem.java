package com.pr0gramm.app.ui.intro.slides;

/**
 */
abstract class ActionItem {
    final String title;

    ActionItem(String title) {
        this.title = title;
    }

    public abstract boolean enabled();

    public abstract void activate();

    public abstract void deactivate();

    @Override
    public String toString() {
        return title;
    }
}
