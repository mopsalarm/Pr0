package com.pr0gramm.app.cache;

import com.pr0gramm.app.cache.access.DataAccess;

import java.io.IOException;
import java.io.InputStream;

/**
 */
public abstract class Loader {
    private final DataAccess dataAccess;

    private InputStream inputStream;
    private int blockIndex;

    protected Loader(DataAccess dataAccess) {
        this.dataAccess = dataAccess;
    }

    protected abstract InputStream open() throws IOException;

    public boolean step() throws IOException {
        if (inputStream == null) {
            inputStream = open();
            return true;
        }

        byte[] block = new byte[dataAccess.getBlockSize()];

        boolean eof = false;

        int read = 0;
        while (!eof && read < block.length) {
            int len = inputStream.read(block, read, block.length - read);
            if (len < 0) {
                eof = true;
            } else {
                read += len;
            }
        }

        dataAccess.write(blockIndex, block);
        blockIndex++;

        return !eof;
    }
}
