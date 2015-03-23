package com.pr0gramm.app.cache;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.BitSet;

/**
 */
public class BinaryCacheEntryPersister implements CacheEntryPersister {
    @Override
    public void persist(CacheEntry cacheEntry, OutputStream output) throws IOException {
        DataOutputStream data = new DataOutputStream(output);
        data.writeUTF(cacheEntry.getUrl());
        data.writeInt(cacheEntry.getSize());

        byte[] bytes = cacheEntry.getAvailable().toByteArray();
        data.writeInt(bytes.length);
        data.write(bytes);
        data.flush();
    }

    @Override
    public CacheEntry load(InputStream stream) throws IOException {
        DataInputStream data = new DataInputStream(stream);

        // get basic data for entry
        String url = data.readUTF();
        int size = data.readInt();

        // read the available-blocks bitset
        byte[] bytes = new byte[data.readInt()];
        data.readFully(bytes);
        BitSet available = BitSet.valueOf(bytes);

        return new CacheEntry(url, size, available);
    }
}
