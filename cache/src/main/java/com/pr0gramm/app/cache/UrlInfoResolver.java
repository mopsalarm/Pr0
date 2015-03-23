package com.pr0gramm.app.cache;

import java.io.IOException;

/**
 */
public interface UrlInfoResolver {
    /**
     * Tries to resolve the given url to provide the size of the url.
     *
     * @param url The url to resolve.
     * @throws IOException
     */
    UrlInfo resolve(String url) throws IOException;


    public static class UrlInfo {
        private final String url;
        private final int size;

        public UrlInfo(String url, int size) {
            this.url = url;
            this.size = size;
        }

        public String getUrl() {
            return url;
        }

        public int getSize() {
            return size;
        }
    }
}
