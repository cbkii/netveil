package io.github.cbkii.netveil.country;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

/** Lightweight JobScheduler-backed country pack refresh settings. */
public final class CountryRefreshScheduler {
    public static final String PREFS = "country_data";
    public static final String KEY_AUTO = "auto_refresh";
    public static final String KEY_FREQUENCY = "refresh_frequency";
    public static final String KEY_LAST_SUCCESS = "last_refresh_success";
    public static final String KEY_LAST_ERROR = "last_refresh_error";
    public static final String KEY_SCHEDULE_ERROR = "refresh_schedule_error";
    private static final int JOB_ID = 0x4e5650;

    public enum Frequency {
        MONTHLY("monthly", 30L * 24L * 60L * 60L * 1000L),
        WEEKLY("weekly", 7L * 24L * 60L * 60L * 1000L),
        DAILY("daily", 24L * 60L * 60L * 1000L);

        public final String storedValue;
        public final long intervalMillis;
        Frequency(String storedValue, long intervalMillis) {
            this.storedValue = storedValue;
            this.intervalMillis = intervalMillis;
        }

        public static Frequency fromStored(String value) {
            for (Frequency frequency : values()) {
                if (frequency.storedValue.equals(value)) return frequency;
            }
            return MONTHLY;
        }
    }

    public static final class ScheduleResult {
        public final boolean success;
        public final String error;

        private ScheduleResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }

        static ScheduleResult success() {
            return new ScheduleResult(true, null);
        }

        static ScheduleResult failure(String error) {
            return new ScheduleResult(false,
                    error == null || error.isBlank() ? "JobScheduler rejected the refresh job" : error);
        }
    }

    private CountryRefreshScheduler() {}

    public static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context context) {
        return preferences(context).getBoolean(KEY_AUTO, false);
    }

    public static Frequency frequency(Context context) {
        return Frequency.fromStored(preferences(context).getString(
                KEY_FREQUENCY, Frequency.MONTHLY.storedValue));
    }

    public static String scheduleError(Context context) {
        return preferences(context).getString(KEY_SCHEDULE_ERROR, "");
    }

    /**
     * Applies the user's requested automatic-refresh state transactionally. A failed enable never
     * leaves KEY_AUTO=true, so a runtime scheduling problem cannot become a persistent launch crash.
     */
    public static ScheduleResult configure(Context context, boolean enabled, Frequency frequency) {
        SharedPreferences prefs = preferences(context);
        if (!enabled) {
            ScheduleResult cancelled = cancel(context);
            SharedPreferences.Editor editor = prefs.edit()
                    .putBoolean(KEY_AUTO, false)
                    .putString(KEY_FREQUENCY, frequency.storedValue);
            if (cancelled.success) editor.remove(KEY_SCHEDULE_ERROR);
            else editor.putString(KEY_SCHEDULE_ERROR, cancelled.error);
            editor.apply();
            return cancelled;
        }

        ScheduleResult scheduled = schedule(context, frequency);
        SharedPreferences.Editor editor = prefs.edit()
                .putBoolean(KEY_AUTO, scheduled.success)
                .putString(KEY_FREQUENCY, frequency.storedValue);
        if (scheduled.success) editor.remove(KEY_SCHEDULE_ERROR);
        else editor.putString(KEY_SCHEDULE_ERROR, scheduled.error);
        editor.apply();
        return scheduled;
    }

    /**
     * Re-establishes a persisted periodic job without allowing JobScheduler failures to escape into
     * Activity startup. If restoration fails, automatic refresh is disabled until the user retries.
     */
    public static ScheduleResult ensureScheduled(Context context) {
        if (!enabled(context)) return ScheduleResult.success();
        Frequency frequency = frequency(context);
        ScheduleResult result = schedule(context, frequency);
        SharedPreferences.Editor editor = preferences(context).edit();
        if (result.success) {
            editor.remove(KEY_SCHEDULE_ERROR);
        } else {
            editor.putBoolean(KEY_AUTO, false)
                    .putString(KEY_SCHEDULE_ERROR, result.error);
        }
        editor.apply();
        return result;
    }

    private static ScheduleResult schedule(Context context, Frequency frequency) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return ScheduleResult.failure("JobScheduler is unavailable");
        JobInfo job = new JobInfo.Builder(JOB_ID,
                new ComponentName(context, CountryRefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(frequency.intervalMillis)
                .build();
        try {
            int result = scheduler.schedule(job);
            return result == JobScheduler.RESULT_SUCCESS
                    ? ScheduleResult.success()
                    : ScheduleResult.failure("JobScheduler rejected the refresh job");
        } catch (RuntimeException e) {
            return ScheduleResult.failure(safeMessage(e));
        }
    }

    private static ScheduleResult cancel(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return ScheduleResult.success();
        try {
            scheduler.cancel(JOB_ID);
            return ScheduleResult.success();
        } catch (RuntimeException e) {
            return ScheduleResult.failure(safeMessage(e));
        }
    }

    private static String safeMessage(RuntimeException e) {
        String message = e.getMessage();
        return e.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
