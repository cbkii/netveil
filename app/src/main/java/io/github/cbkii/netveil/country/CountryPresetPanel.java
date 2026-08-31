package io.github.cbkii.netveil.country;

import android.app.Activity;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import io.github.cbkii.netveil.ui.UiFactory;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small platform-only UI for populating profile IPv4 identities from the country pack. */
public final class CountryPresetPanel {
    private static final String KEY_COUNTRY = "selected_country";
    private static final String KEY_HIGH_ONLY = "high_confidence_only";
    private static final String KEY_EXCLUDE_ANON = "exclude_anonymous";
    private static final String[] COUNTRY_LABELS = {
            "Australia", "United States", "United Kingdom", "Indonesia", "France"
    };
    private static final String[] COUNTRY_CODES = {"AU", "US", "GB", "ID", "FR"};
    private static final String[] FREQUENCY_LABELS = {"Monthly", "Weekly", "Daily"};
    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter
            .ofPattern("d MMM uuuu HH:mm 'UTC'", Locale.UK)
            .withZone(ZoneOffset.UTC);

    public interface Listener {
        void onApply(List<String> ipv4Values, boolean replace);
    }

    private final Activity activity;
    private final Listener listener;
    private final SharedPreferences settings;
    private final UiFactory ui;
    private final LinearLayout root;
    private final Spinner country;
    private final Switch highOnly;
    private final Switch excludeAnonymous;
    private final Switch autoRefresh;
    private final Spinner frequency;
    private final TextView status;
    private final Button refresh;
    private CountryPackStore.Loaded loaded;
    private boolean updatingAutoRefresh;

    public CountryPresetPanel(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.settings = CountryRefreshScheduler.preferences(activity);
        this.ui = new UiFactory(activity);
        this.root = ui.vertical();

        // Re-establish a persisted job before reflecting its state in the switch. Scheduling
        // failures are contained by CountryRefreshScheduler and disable automatic refresh safely.
        CountryRefreshScheduler.ensureScheduled(activity);

        root.addView(ui.helper(
                "Adds representative ISP/access-provider IPv4 candidates. Imported identities omit "
                        + "gateway and route metadata; NetVeil never probes candidate addresses."));

        root.addView(ui.label("Country"));
        country = ui.spinner(COUNTRY_LABELS);
        root.addView(country);

        status = ui.status("", UiFactory.Tone.INFO);
        root.addView(status);

        highOnly = ui.switchControl("High-confidence providers only",
                settings.getBoolean(KEY_HIGH_ONLY, true));
        excludeAnonymous = ui.switchControl("Exclude known VPN / proxy / Tor addresses",
                settings.getBoolean(KEY_EXCLUDE_ANON, true));
        root.addView(highOnly);
        root.addView(excludeAnonymous);

        LinearLayout actions = ui.row();
        Button add = ui.button("Add to list", UiFactory.ButtonKind.TEAL);
        Button replace = ui.button("Replace list", UiFactory.ButtonKind.OUTLINE);
        LinearLayout.LayoutParams addParams = ui.weightedParams(1f);
        addParams.rightMargin = ui.dp(6);
        LinearLayout.LayoutParams replaceParams = ui.weightedParams(1f);
        replaceParams.leftMargin = ui.dp(6);
        actions.addView(add, addParams);
        actions.addView(replace, replaceParams);
        actions.setLayoutParams(ui.blockParams(4));
        root.addView(actions);

        root.addView(ui.divider());
        root.addView(ui.subheading("Country data"));
        root.addView(ui.helper(
                "Refresh checks the latest validated NetVeil candidate dataset online. It does not "
                        + "generate candidates on this device or rewrite saved profile values."));

        refresh = ui.button("Refresh now", UiFactory.ButtonKind.TONAL);
        refresh.setLayoutParams(ui.matchWrap());
        root.addView(refresh);

        autoRefresh = ui.switchControl(
                "Automatic refresh", CountryRefreshScheduler.enabled(activity));
        root.addView(autoRefresh);

        root.addView(ui.label("Frequency"));
        frequency = ui.spinner(FREQUENCY_LABELS);
        frequency.setSelection(frequencyIndex(CountryRefreshScheduler.frequency(activity)));
        root.addView(frequency);

        root.addView(ui.helper(
                "Automatic refresh is off by default. When enabled, Monthly is the default; "
                        + "Weekly and Daily remain available."));

        String savedCountry = settings.getString(KEY_COUNTRY, "AU");
        country.setSelection(countryIndex(savedCountry));

        add.setOnClickListener(v -> apply(false));
        replace.setOnClickListener(v -> apply(true));
        refresh.setOnClickListener(v -> refreshNow());
        highOnly.setOnCheckedChangeListener((buttonView, checked) -> {
            settings.edit().putBoolean(KEY_HIGH_ONLY, checked).apply();
            updateStatus();
        });
        excludeAnonymous.setOnCheckedChangeListener((buttonView, checked) -> {
            settings.edit().putBoolean(KEY_EXCLUDE_ANON, checked).apply();
            updateStatus();
        });
        country.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                    int position, long id) {
                settings.edit().putString(KEY_COUNTRY, COUNTRY_CODES[position]).apply();
                updateStatus();
            }

            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        autoRefresh.setOnCheckedChangeListener((buttonView, checked) -> {
            if (updatingAutoRefresh) return;
            configureAutomaticRefresh(checked);
        });
        frequency.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                    int position, long id) {
                if (autoRefresh.isChecked()) configureAutomaticRefresh(true);
            }

            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        frequency.setEnabled(autoRefresh.isChecked());
        updateFrequencyVisualState();
        loadLocal();
    }

    public View view() {
        return root;
    }

    /** Current Advanced country selection, used by other draft-population controls such as DNS. */
    public String selectedCountryCode() {
        return selectedCountry();
    }

    private void loadLocal() {
        try {
            loaded = CountryPackStore.loadBest(activity);
            updateStatus();
        } catch (Exception e) {
            loaded = null;
            ui.setStatus(status, UiFactory.Tone.ERROR,
                    "Country data unavailable: " + safeMessage(e));
        }
    }

    private void apply(boolean replace) {
        if (loaded == null) loadLocal();
        if (loaded == null) return;
        List<CountryPack.Candidate> candidates = loaded.pack.candidates(
                selectedCountry(), highOnly.isChecked(), excludeAnonymous.isChecked(),
                CountryPack.DEFAULT_LIMIT);
        if (candidates.isEmpty()) {
            ui.setStatus(status, UiFactory.Tone.WARNING,
                    "No candidates match the selected filters. Relax a filter or refresh data.");
            return;
        }
        List<String> ips = new ArrayList<>();
        for (CountryPack.Candidate candidate : candidates) ips.add(candidate.ipv4);
        listener.onApply(ips, replace);
        ui.setStatus(status, UiFactory.Tone.SUCCESS,
                (replace ? "Replaced with " : "Added ") + ips.size() + " "
                        + selectedCountry()
                        + " candidate IPv4 values. Save changes to keep them.");
    }

    private void configureAutomaticRefresh(boolean requested) {
        CountryRefreshScheduler.ScheduleResult result = CountryRefreshScheduler.configure(
                activity, requested, selectedFrequency());
        boolean actual = CountryRefreshScheduler.enabled(activity);
        if (autoRefresh.isChecked() != actual) {
            updatingAutoRefresh = true;
            try {
                autoRefresh.setChecked(actual);
            } finally {
                updatingAutoRefresh = false;
            }
        }
        frequency.setEnabled(actual);
        updateFrequencyVisualState();
        if (!result.success) {
            String action = requested ? "Automatic refresh could not be enabled: "
                    : "Automatic refresh was disabled, but the old job could not be cancelled: ";
            ui.setStatus(status, UiFactory.Tone.WARNING, action + result.error);
        } else {
            updateStatus();
        }
    }

    private void refreshNow() {
        refresh.setEnabled(false);
        ui.setStatus(status, UiFactory.Tone.INFO,
                "Checking the latest validated country data online…");
        CountryPackStore.Source fallbackSource = loaded == null ? null : loaded.source;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                CountryPackStore.RefreshResult result;
                try {
                    result = CountryPackStore.refreshBlocking(activity);
                } catch (Exception e) {
                    result = CountryPackStore.RefreshResult.failure(
                            System.currentTimeMillis(), fallbackSource, safeMessage(e));
                }
                CountryRefreshScheduler.recordRefreshResult(activity, result);
            } catch (RuntimeException e) {
                CountryPackStore.RefreshResult failed = CountryPackStore.RefreshResult.failure(
                        System.currentTimeMillis(), fallbackSource, safeMessage(e));
                try {
                    CountryRefreshScheduler.recordRefreshResult(activity, failed);
                } catch (RuntimeException ignored) {
                    // UI still recovers below even if local metadata persistence itself failed.
                }
            } finally {
                if (!activity.isDestroyed()) {
                    activity.runOnUiThread(() -> {
                        if (activity.isDestroyed()) return;
                        refresh.setEnabled(true);
                        loadLocal();
                    });
                }
                executor.shutdown();
            }
        });
    }

    private void updateStatus() {
        if (loaded == null) return;
        int count = loaded.pack.candidates(selectedCountry(), highOnly.isChecked(),
                excludeAnonymous.isChecked(), CountryPack.DEFAULT_LIMIT).size();
        StringBuilder value = new StringBuilder()
                .append(count).append(" candidates after filters")
                .append("\nDataset: ").append(formatGeneratedAt(loaded.pack.generatedAt))
                .append("\nSource: ").append(loaded.source.label);

        long lastCheck = CountryRefreshScheduler.lastCheckMillis(activity);
        if (lastCheck > 0L) {
            value.append("\nLast checked online: ").append(formatMillis(lastCheck));
        } else {
            value.append("\nNot checked online yet");
        }

        String scheduleError = CountryRefreshScheduler.scheduleError(activity);
        if (scheduleError != null && !scheduleError.isBlank()) {
            ui.setStatus(status, UiFactory.Tone.WARNING,
                    value + "\nAutomatic refresh disabled: " + scheduleError);
            return;
        }

        CountryPackStore.Outcome outcome = CountryRefreshScheduler.lastOutcome(activity);
        String lastError = CountryRefreshScheduler.lastError(activity);
        if (outcome == CountryPackStore.Outcome.FAILED) {
            value.append("\nLast refresh: Refresh failed · ")
                    .append(loaded.source == CountryPackStore.Source.ONLINE_CACHE
                            ? "using previous online cache" : "using bundled APK data");
            if (lastError != null && !lastError.isBlank()) {
                value.append("\n").append(lastError);
            }
            ui.setStatus(status, UiFactory.Tone.WARNING, value.toString());
        } else if (outcome == CountryPackStore.Outcome.UPDATED) {
            value.append("\nLast refresh: Updated online");
            ui.setStatus(status, UiFactory.Tone.SUCCESS, value.toString());
        } else if (outcome == CountryPackStore.Outcome.UNCHANGED) {
            value.append("\nLast refresh: Online data already current");
            ui.setStatus(status, UiFactory.Tone.INFO, value.toString());
        } else {
            ui.setStatus(status, UiFactory.Tone.INFO, value.toString());
        }
    }

    private void updateFrequencyVisualState() {
        frequency.setAlpha(frequency.isEnabled() ? 1f : 0.55f);
    }

    private String selectedCountry() {
        int position = Math.max(0, country.getSelectedItemPosition());
        return COUNTRY_CODES[Math.min(position, COUNTRY_CODES.length - 1)];
    }

    private CountryRefreshScheduler.Frequency selectedFrequency() {
        return switch (frequency.getSelectedItemPosition()) {
            case 1 -> CountryRefreshScheduler.Frequency.WEEKLY;
            case 2 -> CountryRefreshScheduler.Frequency.DAILY;
            default -> CountryRefreshScheduler.Frequency.MONTHLY;
        };
    }

    private static int countryIndex(String code) {
        for (int i = 0; i < COUNTRY_CODES.length; i++) {
            if (COUNTRY_CODES[i].equals(code)) return i;
        }
        return 0;
    }

    private static int frequencyIndex(CountryRefreshScheduler.Frequency frequency) {
        return switch (frequency) {
            case MONTHLY -> 0;
            case WEEKLY -> 1;
            case DAILY -> 2;
        };
    }

    private static String formatGeneratedAt(String generatedAt) {
        try {
            return UTC_TIME.format(Instant.parse(generatedAt));
        } catch (RuntimeException ignored) {
            return generatedAt;
        }
    }

    private static String formatMillis(long millis) {
        return UTC_TIME.format(Instant.ofEpochMilli(millis));
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
