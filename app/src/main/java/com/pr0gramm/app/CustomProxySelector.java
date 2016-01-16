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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 */
public class CustomProxySelector extends ProxySelector {
    private static final Logger logger = LoggerFactory.getLogger("CustomProxySelector");
    private final AtomicBoolean active = new AtomicBoolean();

    @Override
    public List<Proxy> select(URI uri) {
        if (active.get() && "pr0gramm.com".equals(uri.getHost())) {
            if (uri.getPath().startsWith("/api/") && !"/api/user/login".equals(uri.getPath())) {
                logger.info("Using proxy for {}", uri);

                InetSocketAddress address = new InetSocketAddress("pr0.wibbly-wobbly.de", 80);
                Proxy proxy = new Proxy(Proxy.Type.HTTP, address);
                return Collections.singletonList(proxy);
            }
        }

        return Collections.singletonList(Proxy.NO_PROXY);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress address, IOException failure) {
    }

    public void setActive(boolean active) {
        logger.info("Switch active to: {}", active);
        this.active.set(active);
    }
}
