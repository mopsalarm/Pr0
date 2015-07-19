package com.pr0gramm.app.vpx;

import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 */
class InputStreamDataSource implements org.ebml.io.DataSource {
    private final InputStream stream;
    private int position;

    InputStreamDataSource(InputStream stream) {
        this.stream = stream;
    }

    @Override
    public byte readByte() {
        try {
            int result = stream.read();
            position++;
            return (byte) result;
        } catch (IOException err) {
            throw Throwables.propagate(err);
        }
    }

    @Override
    public int read(ByteBuffer byteBuffer) {
        try {
            int offset = byteBuffer.arrayOffset() + byteBuffer.position();
            int count = ByteStreams.read(stream, byteBuffer.array(), offset, byteBuffer.remaining());
            byteBuffer.position(byteBuffer.position() + count);
            position += count;
            return count;
        } catch (IOException err) {
            throw Throwables.propagate(err);
        }
    }

    @Override
    public long skip(long l) {
        try {
            long skipped = stream.skip(l);
            position += (int) skipped;
            return skipped;
        } catch (IOException err) {
            throw Throwables.propagate(err);
        }
    }

    @Override
    public long length() {
        return -1;
    }

    @Override
    public long getFilePointer() {
        return position;
    }

    @Override
    public boolean isSeekable() {
        return false;
    }

    @Override
    public long seek(long l) {
        throw new UnsupportedOperationException();
    }
}
