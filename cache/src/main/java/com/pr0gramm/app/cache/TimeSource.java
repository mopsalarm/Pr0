package com.pr0gramm.app.cache;

import static com.google.common.base.Preconditions.checkArgument;

/**
 */
public interface TimeSource {
    /**
     * Returns the milli seconds from epoch to now.
     *
     * @return The number of milliseconds from epoch to now-
     */
    long now();


    public static class SystemTimeSource implements TimeSource {
        @Override
        public long now() {
            return System.currentTimeMillis();
        }
    }

    public static class TestTimeSource implements TimeSource {
        private long time;

        public TestTimeSource(long time) {
            this.time = time;
        }

        @Override
        public long now() {
            return time;
        }

        /**
         * Modifies this time source and advances the time by the given offset.
         *
         * @param offset The offset to add to the current time
         */
        public void advance(long offset) {
            checkArgument(offset >= 0, "Time offset must not be negative");
            this.time = offset;
        }
    }
}
