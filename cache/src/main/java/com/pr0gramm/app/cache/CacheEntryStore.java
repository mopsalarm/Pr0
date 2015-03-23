package com.pr0gramm.app.cache;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 */
public class CacheEntryStore {
    private final File directory;
    private final CacheEntryPersister persister;

    public CacheEntryStore(File directory, CacheEntryPersister persister) {
        this.directory = directory;
        this.persister = persister;
    }

    public void store(CacheEntry entry) {
        File file = fileOf(entry, "info");
        try (OutputStream output = new FileOutputStream(file)) {
            persister.persist(entry, output);

        } catch (IOException ioError) {
            throw Throwables.propagate(ioError);
        }
    }

    /**
     * Loads or creates a new cache entry for the given entry.
     */
    public CacheEntry acquire(String url, int size) {
        return load(url).or(() -> new CacheEntry(url, size));
    }

    /**
     * Loads the cache entry for the given url. Will not fail with
     * an exception, if the file does not exist, but will fail, if the
     * file could not be read.
     *
     * @param url The url of the cache entry to get.
     */
    public Optional<CacheEntry> load(String url) {
        File file = fileOf(url, "info");
        try {
            try (InputStream input = new FileInputStream(file)) {
                return Optional.of(persister.load(input));
            }
        } catch (FileNotFoundException error) {
            return Optional.absent();

        } catch (IOException ioError) {
            throw Throwables.propagate(ioError);
        }
    }

    /**
     * Returns the name of the datafile for the given  cache entry.
     *
     * @param entry The cache entry to get the datafile for.
     */
    public File dataFile(CacheEntry entry) {
        return fileOf(entry, "data");
    }

    private File fileOf(CacheEntry cacheEntry, String suffix) {
        return fileOf(cacheEntry.getUrl(), suffix);
    }

    private File fileOf(String url, String suffix) {
        String prefix = url
                .toLowerCase()
                .replaceFirst("https?://", "")
                .replaceAll("[^a-z0-9]", "_");

        return new File(directory, prefix + "." + suffix);
    }
}
