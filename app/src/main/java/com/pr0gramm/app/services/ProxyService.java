package com.pr0gramm.app.services;

/**
 */
public interface ProxyService {
    /**
     * Rewrites the URL to use the given proxy.
     *
     * @param url The url to proxy
     */
    String proxy(String url);
}
