package com.pr0gramm.app.cache.access;

import java.io.File;
import java.io.IOException;

/**
 */
public interface DataAccess {
    /**
     * Reads the given block into the output buffer. The output buffer must have
     * the size of a block.
     *
     * @param block    The block to read
     * @param output   The output to copy the read bytes to
     */
    void read(int block, byte[] output) throws IOException;

    /**
     * Stores the the given bytes into the given block. The bytes
     * must be of the size of one block.
     */
    void write(int block, byte[] bytes) throws IOException;

    /**
     * Closes the access file when we are done with it.
     * No further calls to read or write are allowed.
     */
    void close();

    /**
     * Gets the block size of this {@link com.pr0gramm.app.cache.access.DataAccess} instance
     */
    int getBlockSize();

    public interface DataAccessFactory {
        /**
         * Creates a new {@link DataAccess} instance for
         * the given file, block size and number of blocks.
         */
        DataAccess newDataAccess(File file, int blockSize, int blockCount) throws IOException;
    }
}
