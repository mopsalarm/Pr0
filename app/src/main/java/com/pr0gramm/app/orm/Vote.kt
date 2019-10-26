package com.pr0gramm.app.orm

/**
 */
enum class Vote(val voteValue: Int) {
    DOWN(-1), NEUTRAL(0), UP(1), FAVORITE(2);

    companion object {
        private val votes = Vote.values()

        fun ofVoteValue(value: Int): Vote {
            return votes[value + 1]
        }
    }
}
