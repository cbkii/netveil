package io.github.cbkii.netveil.country;

import android.app.job.JobParameters;
import android.app.job.JobService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CountryRefreshJobService extends JobService {
    private ExecutorService executor;
    private volatile boolean stopped;

    @Override
    public boolean onStartJob(JobParameters params) {
        // A stale persisted job must never run after the user disabled automatic refresh or after
        // scheduler restoration failed and disabled the preference fail-safe.
        if (!CountryRefreshScheduler.enabled(this)) return false;

        stopped = false;
        ExecutorService localExecutor = Executors.newSingleThreadExecutor();
        executor = localExecutor;
        localExecutor.execute(() -> {
            try {
                CountryPackStore.RefreshResult result = CountryPackStore.refreshBlocking(this);
                if (stopped) return;
                CountryRefreshScheduler.recordRefreshResult(this, result);

                // This is already a periodic job. A transient download failure must not create an
                // extra retry cadence outside the user's Monthly/Weekly/Daily choice.
                jobFinished(params, false);
            } finally {
                localExecutor.shutdown();
                if (executor == localExecutor) executor = null;
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        stopped = true;
        ExecutorService current = executor;
        if (current != null) current.shutdownNow();
        // Keep the configured periodic cadence instead of requesting an immediate/backoff retry.
        return false;
    }
}
