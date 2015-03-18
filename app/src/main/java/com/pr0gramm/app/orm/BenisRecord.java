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

    public static List<BenisRecord> getBenisValuesAfter(ReadableInstant time) {
        List<BenisRecord> records = find(BenisRecord.class, "time >= ?", String.valueOf(time.getMillis()));
        return Ordering.natural()
                .onResultOf(BenisRecord::getTimeMillis)
                .sortedCopy(records);
    }

    public static Optional<BenisRecord> getFirstBenisRecordBefore(ReadableInstant time) {
        List<BenisRecord> result = find(BenisRecord.class,
                "time < ?", new String[]{String.valueOf(time.getMillis())},
                null, "time DESC", "1");

        return result.isEmpty() ? Optional.<BenisRecord>absent() : Optional.of(result.get(0));
    }
}
