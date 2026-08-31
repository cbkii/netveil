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
        stopped = false;
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            CountryPackStore.RefreshResult result = CountryPackStore.refreshBlocking(this);
            if (stopped) return;

            var editor = CountryRefreshScheduler.preferences(this).edit();
            if (result.success) {
                editor.putString(CountryRefreshScheduler.KEY_LAST_SUCCESS, result.generatedAt)
                        .remove(CountryRefreshScheduler.KEY_LAST_ERROR);
            } else {
                editor.putString(CountryRefreshScheduler.KEY_LAST_ERROR,
                        result.error == null ? "Refresh failed" : result.error);
            }
            editor.apply();

            // This is already a periodic job. A transient download failure must not create an
            // extra retry cadence outside the user's Monthly/Weekly/Daily choice.
            jobFinished(params, false);
            executor.shutdown();
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        stopped = true;
        if (executor != null) executor.shutdownNow();
        // Keep the configured periodic cadence instead of requesting an immediate/backoff retry.
        return false;
    }
}
