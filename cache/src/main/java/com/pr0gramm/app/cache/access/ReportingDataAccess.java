package com.pr0gramm.app.cache.access;

import com.pr0gramm.app.cache.CacheEntry;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;

/**
 */
public class ReportingDataAccess implements DataAccess {
    private final CacheEntry cacheEntry;
    private final DataAccess dataAccess;

    public ReportingDataAccess(CacheEntry cacheEntry, DataAccess dataAccess) {
        checkArgument(cacheEntry.getBlockSize() == dataAccess.getBlockSize(),
                "Block sizes mismatch");

        this.cacheEntry = cacheEntry;
        this.dataAccess = dataAccess;
    }

    @Override
    public void read(int block, byte[] output) throws IOException {
        dataAccess.read(block, output);
    }

    @Override
    public void write(int block, byte[] bytes) throws IOException {
        dataAccess.write(block, bytes);
        cacheEntry.write(block);
    }

    @Override
    public int getBlockSize() {
        return dataAccess.getBlockSize();
    }

    @Override
    public void close() {
        dataAccess.close();
    }
}
