package com.pr0gramm.app.feed;

/**
 */
public enum Vote {
    DOWN(-1), NEUTRAL(0), UP(1);
    private final int voteValue;

    Vote(int voteValue) {
        this.voteValue = voteValue;
    }

    public int getVoteValue() {
        return voteValue;
    }
}
