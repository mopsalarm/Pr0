package com.pr0gramm.app.cache.access;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Uses memory mapping for file access
 */
public class MappedDataAccess implements DataAccess {
    private final int blockSize;
    private final int blockCount;
    private MappedByteBuffer buffer;

    private MappedDataAccess(File file, int blockSize, int blockCount) throws IOException {
        this.blockSize = blockSize;
        this.blockCount = blockCount;
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            int byteCount = blockSize * blockCount;
            raf.setLength(byteCount);
            buffer = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, byteCount);
        }
    }

    @Override
    public void read(int block, byte[] output) throws IOException {
        checkState(buffer != null, "File already closed");
        checkArgument(output.length == blockSize);

        ByteBuffer buffer = this.buffer.duplicate();
        buffer.position(blockSize * block);
        buffer.get(output);
    }

    @Override
    public void write(int block, byte[] bytes) throws IOException {
        checkState(buffer != null, "File already closed");
        checkArgument(bytes.length == blockSize);

        ByteBuffer buffer = this.buffer.duplicate();
        buffer.position(blockSize * block);
        buffer.put(bytes);
    }

    @Override
    public void close() {
        // there is no way to manually unmap the file.
        // We need to wait for the gc.
        buffer = null;
    }

    @Override
    public int getBlockSize() {
        return blockSize;
    }

    public static class MappedDataAccessFactory implements DataAccessFactory {
        @Override
        public DataAccess newDataAccess(File file, int blockSize, int blockCount) throws IOException {
            return new MappedDataAccess(file, blockSize, blockCount);
        }
    }
}
