package com.pr0gramm.app.orm

/**
 */
enum class Vote(val voteValue: Int) {
    DOWN(-1), NEUTRAL(0), UP(1), FAVORITE(2);

    val nextUpVote: Vote get() = if (this === Vote.UP || this === Vote.FAVORITE) Vote.NEUTRAL else Vote.UP

    val nextDownVote: Vote get() = if (this === Vote.DOWN) Vote.NEUTRAL else Vote.DOWN

    companion object {
        private val votes = Vote.values()

        fun ofVoteValue(value: Int): Vote {
            return votes[value + 1]
        }
    }
}
