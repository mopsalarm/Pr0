package com.pr0gramm.app.ui.views

/**
 * A comment score.
 */
data class CommentScore(val up: Int, val down: Int) {
    val score: Int get() = up - down
}
