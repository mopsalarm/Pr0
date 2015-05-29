package com.pr0gramm.app.orm;

import com.google.common.base.Optional;
import com.google.common.collect.Ordering;
import com.orm.SugarRecord;

import org.joda.time.Instant;
import org.joda.time.ReadableInstant;

import java.util.List;

/**
 */
public class BenisRecord extends SugarRecord<BenisRecord> {
    private int ownerId;
    private long time;
    private int benis;

    // for sugar orm
    public BenisRecord() {
    }

    public BenisRecord(int ownerId, Instant time, int benis) {
        this.ownerId = ownerId;
        this.time = time.getMillis();
        this.benis = benis;
    }

    public long getTimeMillis() {
        return time;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public Instant getTime() {
        return new Instant(time);
    }

    public int getBenis() {
        return benis;
    }

    public static List<BenisRecord> getBenisValuesAfter(int ownerId, ReadableInstant time) {
        List<BenisRecord> records = find(BenisRecord.class,
                "time >= ? and (owner_id=? or owner_id=0)",
                String.valueOf(time.getMillis()), String.valueOf(ownerId));

        return Ordering.natural()
                .onResultOf(BenisRecord::getTimeMillis)
                .sortedCopy(records);
    }
}
