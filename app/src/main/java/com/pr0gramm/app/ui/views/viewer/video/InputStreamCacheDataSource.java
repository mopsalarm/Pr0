package com.pr0gramm.app.ui.views.viewer.video;

import android.net.Uri;

import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.pr0gramm.app.io.Cache;

import java.io.IOException;
import java.io.InputStream;

/**
 */
class InputStreamCacheDataSource implements BufferedDataSource {
    private final Uri uri;
    private final Cache cache;
    private InputStream inputStream;
    private Cache.Entry entry;

    InputStreamCacheDataSource(Uri uri, Cache cache) {
        this.uri = uri;
        this.cache = cache;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        entry = cache.entryOf(uri);
        inputStream = entry.inputStreamAt((int) dataSpec.position);
        return entry.totalSize();
    }

    @Override
    public void close() {
        if (entry != null) {
            entry.close();
            entry = null;
        }

        Closeables.closeQuietly(inputStream);
        inputStream = null;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        return ByteStreams.read(inputStream, buffer, offset, readLength);
    }

    @Override
    public Uri getUri() {
        return uri;
    }

    /**
     * Returns the percentage that is buffered, or -1, if unknown
     */
    @Override
    public float buffered() {
        return entry.fractionCached();
    }
}
