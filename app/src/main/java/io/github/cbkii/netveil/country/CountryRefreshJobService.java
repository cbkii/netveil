package io.github.cbkii.netveil.country;

import android.app.job.JobParameters;
import android.app.job.JobService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CountryRefreshJobService extends JobService {
    private ExecutorService executor;

    @Override
    public boolean onStartJob(JobParameters params) {
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            CountryPackStore.RefreshResult result = CountryPackStore.refreshBlocking(this);
            var editor = CountryRefreshScheduler.preferences(this).edit();
            if (result.success) {
                editor.putString(CountryRefreshScheduler.KEY_LAST_SUCCESS, result.generatedAt)
                        .remove(CountryRefreshScheduler.KEY_LAST_ERROR);
            } else {
                editor.putString(CountryRefreshScheduler.KEY_LAST_ERROR,
                        result.error == null ? "Refresh failed" : result.error);
            }
            editor.apply();
            jobFinished(params, !result.success);
            executor.shutdown();
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (executor != null) executor.shutdownNow();
        return true;
    }
}
