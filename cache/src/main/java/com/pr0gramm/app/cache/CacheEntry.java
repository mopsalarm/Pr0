package com.pr0gramm.app.cache;

import com.google.common.collect.Sets;

import java.util.BitSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 */
public class CacheEntry {
    public static final int BLOCK_SIZE = 4096;

    // lock for threading operations.
    private final transient Object lock = new Object();

    private final String url;
    private final int size;
    private final BitSet available;

    private transient Set<OnWriteListener> onWriteListeners = Sets.newCopyOnWriteArraySet();

    /**
     * Creates a new and empty cache entry.
     *
     * @param url  The url of the cache entry
     * @param size The size of the data
     */
    public CacheEntry(String url, int size) {
        this(url, size, new BitSet(blockCount(size)));
    }

    /**
     * Returns a cache entry with the given block-mask
     */
    public CacheEntry(String url, int size, BitSet available) {
        checkArgument(available.size() >= blockCount(size));

        this.url = url;
        this.size = size;
        this.available = available;
    }

    public String getUrl() {
        return url;
    }

    public int getSize() {
        return size;
    }

    /**
     * Returns a copy of the bit-set with the blocks that are currently available.
     *
     * @return The bitset with the available blocks of this cache entry.
     */
    public BitSet getAvailable() {
        synchronized (lock) {
            return (BitSet) available.clone();
        }
    }

    public boolean isFullyAvailable() {
        int blockCount = blockCount(size);
        synchronized (lock) {
            return available.nextClearBit(0) >= blockCount;
        }
    }

    public boolean has(int block) {
        synchronized (lock) {
            return available.get(block);
        }
    }

    /**
     * Subscribes the listener to react to writes on this entry.
     *
     * @param onWriteListener The listener to subscribe to this entry.
     */
    public void subscribe(OnWriteListener onWriteListener) {
        onWriteListeners.add(onWriteListener);
    }

    /**
     * Unsubscribes the given listener from this cache entry
     *
     * @param onWriteListener The listener that is to be unsubscribed
     */
    public void unsubscribe(OnWriteListener onWriteListener) {
        onWriteListeners.remove(onWriteListener);
    }

    public int getSubscriberCount() {
        return onWriteListeners.size();
    }

    /**
     * Marks the given block as written
     *
     * @param block The block index that is now written
     */
    public void write(int block) {
        checkBlockIndex(block);

        synchronized (lock) {
            available.set(block);
        }

        afterWrite(block);
    }

    /**
     * After a write was performed, we'll inform the listeners
     *
     * @param block The block index that was just written
     */
    private void afterWrite(int block) {
        for (OnWriteListener listener : onWriteListeners) {
            listener.onWrite(this, block);
        }
    }

    /**
     * Returns the block index for a given position
     */
    public int getBlockIndex(int position) {
        return position / BLOCK_SIZE;
    }

    /**
     * Returns the number of blocks that this {@link com.pr0gramm.app.cache.CacheEntry} has
     *
     * @return The number of blocks
     */
    public int getBlockCount() {
        return blockCount(size);
    }

    /**
     * The block size for this cache entry.
     */
    public int getBlockSize() {
        return BLOCK_SIZE;
    }

    private void checkBlockIndex(int block) {
        if (block * BLOCK_SIZE >= size) {
            String msg = String.format(
                    "Block index %d out of range (must be less than %d)",
                    block, blockCount(size));

            throw new IndexOutOfBoundsException(msg);
        }
    }

    private static int blockCount(int size) {
        return (size + BLOCK_SIZE - 1) / BLOCK_SIZE;
    }
}
