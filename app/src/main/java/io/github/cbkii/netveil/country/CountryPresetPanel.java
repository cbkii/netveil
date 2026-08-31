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

import java.util.ArrayList;
import java.util.List;
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
                "Refresh updates only the cached candidate database. Saved profile values are never "
                        + "rewritten automatically."));

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
        ui.setStatus(status, UiFactory.Tone.INFO, "Refreshing country data…");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                CountryPackStore.RefreshResult result = CountryPackStore.refreshBlocking(activity);
                String refreshError = result.error == null || result.error.isBlank()
                        ? "Refresh failed" : result.error;
                if (result.success) {
                    settings.edit().putString(CountryRefreshScheduler.KEY_LAST_SUCCESS,
                            result.generatedAt).remove(CountryRefreshScheduler.KEY_LAST_ERROR).apply();
                } else {
                    settings.edit().putString(CountryRefreshScheduler.KEY_LAST_ERROR,
                            refreshError).apply();
                }

                if (!activity.isDestroyed()) {
                    activity.runOnUiThread(() -> {
                        if (activity.isDestroyed()) return;
                        refresh.setEnabled(true);
                        if (result.success) {
                            loadLocal();
                        } else {
                            ui.setStatus(status, UiFactory.Tone.WARNING,
                                    "Refresh failed; continuing with the last valid or bundled data. "
                                            + refreshError);
                        }
                    });
                }
            } catch (Exception e) {
                String refreshError = safeMessage(e);
                settings.edit().putString(CountryRefreshScheduler.KEY_LAST_ERROR,
                        refreshError).apply();
                if (!activity.isDestroyed()) {
                    activity.runOnUiThread(() -> {
                        if (activity.isDestroyed()) return;
                        refresh.setEnabled(true);
                        ui.setStatus(status, UiFactory.Tone.WARNING,
                                "Refresh failed; continuing with the last valid or bundled data. "
                                        + refreshError);
                    });
                }
            } finally {
                executor.shutdown();
            }
        });
    }

    private void updateStatus() {
        if (loaded == null) return;
        int count = loaded.pack.candidates(selectedCountry(), highOnly.isChecked(),
                excludeAnonymous.isChecked(), CountryPack.DEFAULT_LIMIT).size();
        String value = count + " candidates after filters · Data " + loaded.pack.generatedAt
                + " (" + loaded.source + ")";
        String scheduleError = CountryRefreshScheduler.scheduleError(activity);
        String lastError = settings.getString(CountryRefreshScheduler.KEY_LAST_ERROR, "");
        if (scheduleError != null && !scheduleError.isBlank()) {
            ui.setStatus(status, UiFactory.Tone.WARNING,
                    value + "\nAutomatic refresh disabled: " + scheduleError);
        } else if (lastError != null && !lastError.isBlank()) {
            ui.setStatus(status, UiFactory.Tone.WARNING,
                    value + "\nLast refresh: " + lastError);
        } else {
            ui.setStatus(status, UiFactory.Tone.INFO, value);
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

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
