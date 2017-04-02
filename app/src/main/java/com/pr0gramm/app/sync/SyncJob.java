package com.pr0gramm.app.sync;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;
import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.support.PersistableBundleCompat;
import com.pr0gramm.app.util.SimpleJobCreator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class SyncJob extends Job {
    public static final JobCreator CREATOR = SimpleJobCreator.forSupplier("sync", SyncJob::new);

    private static final long DEFAULT_SYNC_DELAY_MS = TimeUnit.MINUTES.toMillis(5);

    private static final Logger logger = LoggerFactory.getLogger("SyncJob");

    private SyncJob() {
    }

    @NonNull
    @Override
    protected Result onRunJob(Params params) {
        logger.info("Sync job started.");

        // schedule next sync
        scheduleNextSyncIn(nextInterval(params), TimeUnit.MILLISECONDS);

        // start the sync service that does the real sync.
        logger.info("Start the SyncIntentService now");
        Intent service = new Intent(getContext(), SyncIntentService.class);
        startWakefulService(service);

        return Result.SUCCESS;
    }

    public static void syncNow(Context context) {
        // start normal sync cycle now.
        scheduleNextSync();

        // delegate sync to sync service now.
        Intent intent = new Intent(context, SyncIntentService.class);
        context.startService(intent);
    }

    public static void scheduleNextSync() {
        scheduleNextSyncIn(DEFAULT_SYNC_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    public static void scheduleNextSyncIn(long delay, TimeUnit unit) {
        long delayInMilliseconds = unit.toMillis(delay);
        logger.info("Scheduling sync-job to run in {} seconds", delayInMilliseconds / 1000);

        PersistableBundleCompat extras = new PersistableBundleCompat();
        extras.putLong("delay", delayInMilliseconds);

        JobManager.instance().schedule(new JobRequest.Builder("sync")
                .setUpdateCurrent(true)
                .setPersisted(true)
                .setExact(delayInMilliseconds)
                .setExtras(extras)
                .setRequiredNetworkType(JobRequest.NetworkType.ANY)
                .build());
    }

    private static long nextInterval(Params params) {
        long previousDelay = params.getExtras().getLong("delay", DEFAULT_SYNC_DELAY_MS);
        return Math.min(2 * previousDelay, TimeUnit.HOURS.toMillis(1));
    }
}
