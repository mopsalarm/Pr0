package com.pr0gramm.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 */
public class CustomProxySelector extends ProxySelector {
    private static final Logger logger = LoggerFactory.getLogger(CustomProxySelector.class);

    @Override
    public List<Proxy> select(URI uri) {
        if ("pr0gramm.com".equals(uri.getHost())) {
            if (uri.getPath().startsWith("/api/")) {
                logger.info("Using proxy for {}", uri);

                InetSocketAddress address = new InetSocketAddress("pr0.wibbly-wobbly.de", 8080);
                Proxy proxy = new Proxy(Proxy.Type.HTTP, address);
                return Collections.singletonList(proxy);
            }
        }
        return Collections.emptyList();
    }

    @Override
    public void connectFailed(URI uri, SocketAddress address, IOException failure) {

    }
}
