package com.pr0gramm.app.services;

import android.net.Uri;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 */
@Singleton
public class PreloadProxyService implements ProxyService {
    private static final Logger logger = LoggerFactory.getLogger(PreloadProxyService.class);

    private final ProxyService backend;
    private final PreloadLookupService preloadLookupService;

    @Inject
    public PreloadProxyService(ProxyService backend, PreloadLookupService preloadLookupService) {
        this.backend = backend;
        this.preloadLookupService = preloadLookupService;
    }

    @Override
    public Uri proxy(Uri url) {
        File file = preloadLookupService.file(url);
        if (file.exists()) {
            logger.info("Use preloaded file {}", file);
            return Uri.fromFile(file);
        } else {
            return backend.proxy(url);
        }
    }
}
