package com.pr0gramm.app.cache.access;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import static com.google.common.base.Preconditions.checkArgument;

/**
 */
public class RandomAccessFileDataAccess implements DataAccess {
    private final Object lock = new Object();
    private final RandomAccessFile fp;
    private final int blockSize;

    private RandomAccessFileDataAccess(RandomAccessFile fp, int blockSize) {
        this.fp = fp;
        this.blockSize = blockSize;
    }

    @Override
    public void read(int block, byte[] output) throws IOException {
        checkArgument(output.length == blockSize);

        synchronized (lock) {
            fp.seek(blockSize * block);
            fp.readFully(output, 0, blockSize);
        }
    }

    @Override
    public void write(int block, byte[] bytes) throws IOException {
        checkArgument(bytes.length == blockSize);

        synchronized (lock) {
            fp.seek(blockSize * block);
            fp.write(bytes, 0, blockSize);
        }
    }

    @Override
    public void close() {
        try {
            fp.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public int getBlockSize() {
        return blockSize;
    }

    public static class RandomAccessFileDataAccessFactory implements DataAccessFactory {
        @Override
        public DataAccess newDataAccess(File file, int blockSize, int blockCount) throws IOException {
            RandomAccessFile raf = new RandomAccessFile(file, "rwd");
            raf.setLength(blockSize * blockCount);
            return new RandomAccessFileDataAccess(raf, blockSize);
        }
    }
}
