package com.pr0gramm.app.io;

import com.google.common.io.ByteStreams;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A simple file based entry. This one is backed by a static file and does
 * not require caching at all. Useful for local "file://" urls.
 */
final class FileEntry implements Cache.Entry {
    private final File file;

    FileEntry(File file) {
        this.file = file;
    }

    @Override
    public int totalSize() throws IOException {
        return (int) file.length();
    }

    @Override
    public InputStream inputStreamAt(int offset) throws IOException {
        FileInputStream stream = new FileInputStream(file);

        // skip to the given offset.
        ByteStreams.skipFully(stream, offset);

        return stream;
    }

    @Override
    public float fractionCached() {
        return 1.0f;
    }

    @Override
    public void close() {
    }
}
