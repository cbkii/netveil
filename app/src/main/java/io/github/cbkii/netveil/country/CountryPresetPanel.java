package io.github.cbkii.netveil.country;

import android.app.Activity;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

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
    private final LinearLayout root;
    private final Spinner country;
    private final CheckBox highOnly;
    private final CheckBox excludeAnonymous;
    private final CheckBox autoRefresh;
    private final Spinner frequency;
    private final TextView status;
    private final Button refresh;
    private CountryPackStore.Loaded loaded;

    public CountryPresetPanel(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.settings = CountryRefreshScheduler.preferences(activity);
        this.root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        root.addView(text("Country preset", 16));
        root.addView(text(
                "Adds a small representative list of ISP/access-provider IPv4 candidates. "
                        + "Gateway/routes stay hidden; NetVeil never probes candidate addresses.", 12));
        root.addView(text(
                "Internet access is used only to download NetVeil's public country-data pack. "
                        + "Profiles, installed-app lists and device/network identifiers are not uploaded.", 12));

        root.addView(text("Country", 13));
        country = spinner(COUNTRY_LABELS);
        root.addView(country);

        highOnly = check("Exclude medium/low-confidence providers",
                settings.getBoolean(KEY_HIGH_ONLY, true));
        excludeAnonymous = check("Exclude known VPN / proxy / Tor addresses",
                settings.getBoolean(KEY_EXCLUDE_ANON, true));
        root.addView(highOnly);
        root.addView(excludeAnonymous);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button add = button("Add to list");
        Button replace = button("Replace list");
        actions.addView(add);
        actions.addView(replace);
        root.addView(actions);

        status = text("", 12);
        root.addView(status);
        refresh = fullButton("Refresh country data now");
        root.addView(refresh);

        autoRefresh = check("Automatic source refresh", CountryRefreshScheduler.enabled(activity));
        root.addView(autoRefresh);
        root.addView(text("Frequency", 13));
        frequency = spinner(FREQUENCY_LABELS);
        frequency.setSelection(frequencyIndex(CountryRefreshScheduler.frequency(activity)));
        root.addView(frequency);
        root.addView(text(
                "Automatic refresh is off by default. It only updates the cached country database; "
                        + "saved profile IPv4 values are never rewritten automatically.", 12));

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
        country.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                settings.edit().putString(KEY_COUNTRY, COUNTRY_CODES[position]).apply();
                updateStatus();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        autoRefresh.setOnCheckedChangeListener((buttonView, checked) -> {
            CountryRefreshScheduler.configure(activity, checked, selectedFrequency());
            frequency.setEnabled(checked);
            updateStatus();
        });
        frequency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (autoRefresh.isChecked()) {
                    CountryRefreshScheduler.configure(activity, true, selectedFrequency());
                    updateStatus();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        frequency.setEnabled(autoRefresh.isChecked());
        CountryRefreshScheduler.ensureScheduled(activity);
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
            status.setText("⚠ Country data unavailable: " + safeMessage(e));
        }
    }

    private void apply(boolean replace) {
        if (loaded == null) loadLocal();
        if (loaded == null) return;
        List<CountryPack.Candidate> candidates = loaded.pack.candidates(
                selectedCountry(), highOnly.isChecked(), excludeAnonymous.isChecked(),
                CountryPack.DEFAULT_LIMIT);
        if (candidates.isEmpty()) {
            status.setText("⚠ No candidates match the selected filters. Relax a filter or refresh data.");
            return;
        }
        List<String> ips = new ArrayList<>();
        for (CountryPack.Candidate candidate : candidates) ips.add(candidate.ipv4);
        listener.onApply(ips, replace);
        status.setText((replace ? "Replaced with " : "Added ") + ips.size()
                + " " + selectedCountry() + " candidate IPv4 values. Save the profile to keep them.");
    }

    private void refreshNow() {
        refresh.setEnabled(false);
        status.setText("Refreshing country data…");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                CountryPackStore.RefreshResult result = CountryPackStore.refreshBlocking(activity);
                if (result.success) {
                    settings.edit().putString(CountryRefreshScheduler.KEY_LAST_SUCCESS,
                            result.generatedAt).remove(CountryRefreshScheduler.KEY_LAST_ERROR).apply();
                } else {
                    settings.edit().putString(CountryRefreshScheduler.KEY_LAST_ERROR,
                            result.error == null ? "Refresh failed" : result.error).apply();
                }

                if (!activity.isDestroyed()) {
                    activity.runOnUiThread(() -> {
                        if (activity.isDestroyed()) return;
                        refresh.setEnabled(true);
                        if (result.success) {
                            loadLocal();
                        } else {
                            status.setText("⚠ Refresh failed; continuing with last valid/bundled data. "
                                    + (result.error == null ? "Refresh failed" : result.error));
                        }
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
        StringBuilder value = new StringBuilder("Data: ")
                .append(loaded.pack.generatedAt).append(" (").append(loaded.source).append(") · ")
                .append(count).append(" candidates after filters");
        String lastError = settings.getString(CountryRefreshScheduler.KEY_LAST_ERROR, "");
        if (lastError != null && !lastError.isBlank()) {
            value.append("\n⚠ Last refresh: ").append(lastError);
        }
        status.setText(value.toString());
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
        for (int i = 0; i < COUNTRY_CODES.length; i++) if (COUNTRY_CODES[i].equals(code)) return i;
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

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setLayoutParams(matchWrap());
        return spinner;
    }

    private CheckBox check(String label, boolean checked) {
        CheckBox box = new CheckBox(activity);
        box.setText(label);
        box.setChecked(checked);
        return box;
    }

    private Button button(String label) {
        Button button = new Button(activity);
        button.setText(label);
        button.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return button;
    }

    private Button fullButton(String label) {
        Button button = new Button(activity);
        button.setText(label);
        button.setLayoutParams(matchWrap());
        return button;
    }

    private TextView text(String value, float size) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setPadding(0, dp(5), 0, dp(5));
        return text;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
