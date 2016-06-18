package com.pr0gramm.app.ui.views.viewer.video;

import com.google.android.exoplayer.upstream.DataSource;

/**
 * A datasource that forwards all requests to another one.
 */
public interface BufferedDataSource extends DataSource {
    /**
     * Returns the fraction of the file that is already buffered
     */
    float buffered();
}
