package com.pr0gramm.app.services;

import android.net.Uri;

/**
 */
public interface ProxyService {
    /**
     * Rewrites the URL to use the given proxy.
     *
     * @param url The url to proxy
     */
    Uri proxy(Uri url);
}
