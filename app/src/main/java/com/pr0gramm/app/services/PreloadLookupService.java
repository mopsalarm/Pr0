package com.pr0gramm.app.services;

import android.app.Application;
import android.net.Uri;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

@Singleton
public class PreloadLookupService {
    private static final Logger logger = LoggerFactory.getLogger(PreloadLookupService.class);

    private final File preloadCache;

    @Inject
    public PreloadLookupService(Application application) {
        preloadCache = new File(application.getCacheDir(), "preload");
        if (preloadCache.mkdirs()) {
            logger.info("preload directory created at {}", preloadCache);
        }
    }

    public Optional<InputStream> open(Uri uri) {
        try {
            return Optional.of(new FileInputStream(file(uri)));
        } catch (FileNotFoundException e) {
            return Optional.absent();
        }
    }

    public boolean exists(Uri uri) {
        return file(uri).exists();
    }

    public File file(Uri uri) {
        String filename = uri.toString().replaceFirst("https?://", "").replaceAll("[^0-9a-zA-Z.]+", "_");
        return new File(preloadCache, filename);
    }
}
