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

    public static void configure(Context context, boolean enabled, Frequency frequency) {
        preferences(context).edit()
                .putBoolean(KEY_AUTO, enabled)
                .putString(KEY_FREQUENCY, frequency.storedValue)
                .apply();
        if (enabled) schedule(context, frequency); else cancel(context);
    }

    public static void ensureScheduled(Context context) {
        if (enabled(context)) schedule(context, frequency(context));
    }

    private static void schedule(Context context, Frequency frequency) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID,
                new ComponentName(context, CountryRefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(frequency.intervalMillis)
                .build();
        scheduler.schedule(job);
    }

    private static void cancel(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler != null) scheduler.cancel(JOB_ID);
    }
}
