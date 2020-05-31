package com.pr0gramm.app.orm

/**
 */
enum class Vote(val voteValue: Int) {
    DOWN(-1), NEUTRAL(0), UP(1);

    companion object {
        fun ofVoteValue(value: Int): Vote {
            return when {
                value > 0 -> UP
                value < 0 -> DOWN
                else -> NEUTRAL
            }
        }
    }
}
