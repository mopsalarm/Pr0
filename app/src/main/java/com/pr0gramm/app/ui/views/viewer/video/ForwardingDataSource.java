package com.pr0gramm.app.ui.views.viewer.video;

import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;

import java.io.IOException;

/**
 */
public class ForwardingDataSource implements BufferedDataSource {
    private final DataSource dataSource;

    public ForwardingDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public float buffered() {
        return -1;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        return dataSource.open(dataSpec);
    }

    @Override
    public void close() throws IOException {
        dataSource.close();
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        return dataSource.read(buffer, offset, readLength);
    }
}
