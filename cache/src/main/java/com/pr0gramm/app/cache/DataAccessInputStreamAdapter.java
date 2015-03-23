package com.pr0gramm.app.cache;

import com.pr0gramm.app.cache.access.DataAccess;

import java.io.IOException;
import java.io.InputStream;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Adapts a {@link com.pr0gramm.app.cache.access.DataAccess} into an input stream.
 */
public class DataAccessInputStreamAdapter extends InputStream {
    protected final DataAccess dataAccess;
    private final int blockCount;
    private final int blockSize;
    private int blockIndex;

    public DataAccessInputStreamAdapter(DataAccess dataAccess, int blockCount) {
        this.dataAccess = dataAccess;
        this.blockCount = blockCount;
        this.blockSize = dataAccess.getBlockSize();
    }

    @Override
    public int read() throws IOException {
        throw new UnsupportedOperationException("Reading of just one byte is not supported");
    }

    @Override
    public int read(byte[] bytes, int offset, int len) throws IOException {
        checkArgument(offset == 0, "Offset must be zero");
        checkArgument(len == bytes.length, "Length must be equal to the length of the array");
        checkArgument(bytes.length == blockSize, "Can only read exactly one block");

        dataAccess.read(blockIndex, bytes);
        blockIndex++;

        return bytes.length;
    }

    @Override
    public long skip(long n) throws IOException {
        checkArgument(n % blockSize == 0 && n > 0,
                "Invalid number of bytes to skip");

        int previous = blockIndex;
        int amount = (int) (n / blockSize);
        blockIndex = Math.min(blockCount, blockIndex + amount);
        return blockIndex - previous;
    }
}
