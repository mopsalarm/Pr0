package com.pr0gramm.app.ui.intro.slides

/**
 */
abstract class ActionItem(val title: String) {

    abstract fun enabled(): Boolean

    abstract fun activate()

    abstract fun deactivate()

    override fun toString(): String {
        return title
    }
}
