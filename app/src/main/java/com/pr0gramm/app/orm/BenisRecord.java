package com.pr0gramm.app.orm;

import com.google.common.collect.Ordering;
import com.orm.SugarRecord;

import org.joda.time.Instant;
import org.joda.time.ReadableInstant;

import java.util.List;

/**
 */
public class BenisRecord extends SugarRecord<BenisRecord> {
    private long time;
    private int benis;

    // for sugar orm
    public BenisRecord() {
    }

    public BenisRecord(Instant time, int benis) {
        this.time = time.getMillis();
        this.benis = benis;
    }

    public long getTimeMillis() {
        return time;
    }

    public Instant getTime() {
        return new Instant(time);
    }

    public int getBenis() {
        return benis;
    }

    public List<BenisRecord> getBenisValuesAfter(ReadableInstant time) {
        List<BenisRecord> records = find(BenisRecord.class, "time >= ?", String.valueOf(time.getMillis()));
        return Ordering.natural()
                .onResultOf(BenisRecord::getTimeMillis)
                .sortedCopy(records);
    }
}
