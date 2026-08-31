package io.github.cbkii.netveil;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Insets;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import io.github.cbkii.netveil.config.ConfigKeys;
import io.github.cbkii.netveil.config.Ipv4;
import io.github.cbkii.netveil.config.NetworkIdentity;
import io.github.cbkii.netveil.config.Profile;
import io.github.cbkii.netveil.country.CountryPresetPanel;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lightweight platform-only configuration UI. Vector/LSPosed remains the outer scope gate. */
public final class MainActivity extends Activity {
    private static final String MODULE_PACKAGE = "dev.ip.netveil";
    private static final String GLOBAL_LABEL = "★ All scoped apps (Global)";
    private static final String[] POLICY_LABELS = {
            "Inherit Global", "Custom override", "Disable NetVeil for this app"
    };
    private static final String[] ROUTE_LABELS = {
            "Hide gateway/routes", "Explicit virtual network"
    };

    private final SecureRandom random = new SecureRandom();
    private final List<TargetEntry> targets = new ArrayList<>();
    private final List<IdentityEditor> identityEditors = new ArrayList<>();

    private SharedPreferences prefs;
    private ScrollView scroll;
    private LinearLayout root;
    private AutoCompleteTextView targetField;
    private LinearLayout policyContainer;
    private Spinner policySpinner;
    private LinearLayout profileBody;
    private CheckBox enabled;
    private CheckBox randomize;
    private CheckBox hideVpn;
    private CheckBox hideProxy;
    private CheckBox hideIpv6;
    private LinearLayout identitiesContainer;
    private EditText dns;
    private TextView dnsStatus;
    private TextView validationSummary;
    private TextView preview;
    private Button reroll;
    private Button delete;

    private String selectedTarget = ConfigKeys.GLOBAL;
    private String editorLoadedFor;
    private long currentSeed;
    private boolean loading = true;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(ConfigKeys.PREFS, MODE_PRIVATE);

        scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            root.setPadding(
                    pad + bars.left,
                    pad + bars.top,
                    pad + bars.right,
                    pad + bars.bottom);
            return insets;
        });

        root.addView(text("NetVeil", 24));
        root.addView(text(
                "API-visible network identity masking for Android 15+. NetVeil does not reroute "
                        + "traffic or alter the public IP seen by remote servers.", 14));
        root.addView(helper(
                "Vector / LSPosed scope is the execution gate. Global applies only to apps already "
                        + "scoped there; NetVeil does not broaden framework scope itself."));

        root.addView(section("Profile"));
        root.addView(label("Target profile"));
        targetField = new AutoCompleteTextView(this);
        targetField.setSingleLine(true);
        targetField.setThreshold(0);
        targetField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        targetField.setHint("Choose Global/app, or type a package name");
        targetField.setLayoutParams(matchWrap());
        targetField.setOnClickListener(v -> targetField.showDropDown());
        targetField.setOnFocusChangeListener((v, focused) -> {
            if (focused) targetField.showDropDown();
        });
        targetField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                loadTargetFromField();
                return true;
            }
            return false;
        });
        root.addView(targetField);
        root.addView(helper(
                "The dropdown includes Global, saved/custom package names, and launchable installed apps. "
                        + "You can also type an exact package name manually."));
        Button loadTarget = fullButton("Load selected profile");
        loadTarget.setOnClickListener(v -> loadTargetFromField());
        root.addView(loadTarget);

        policyContainer = new LinearLayout(this);
        policyContainer.setOrientation(LinearLayout.VERTICAL);
        policyContainer.addView(label("Per-app profile mode"));
        policySpinner = spinner(POLICY_LABELS);
        policyContainer.addView(policySpinner);
        policyContainer.addView(helper(
                "Inherit uses Global. Custom stores an override. Disabled exempts this app even while "
                        + "it remains scoped in Vector / LSPosed."));
        root.addView(policyContainer);
        policySpinner.setOnItemSelectedListener(new SimpleSelectionListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!loading) onPolicyChanged();
            }
        });

        profileBody = new LinearLayout(this);
        profileBody.setOrientation(LinearLayout.VERTICAL);
        root.addView(profileBody);

        enabled = check("Enable this profile", true);
        profileBody.addView(enabled);

        profileBody.addView(section("Network identities"));
        profileBody.addView(helper(
                "Each entry is a complete identity. The default hides gateway/routes, so arbitrary "
                        + "IPv4 values do not need a fake subnet or the /0 workaround."));
        identitiesContainer = new LinearLayout(this);
        identitiesContainer.setOrientation(LinearLayout.VERTICAL);
        profileBody.addView(identitiesContainer);
        Button addIdentity = fullButton("+ Add network identity");
        addIdentity.setOnClickListener(v -> {
            addIdentityEditor(null);
            validateAndPreview();
        });
        profileBody.addView(addIdentity);

        profileBody.addView(section("Country IPv4 preset"));
        CountryPresetPanel countryPresetPanel = new CountryPresetPanel(this, this::applyCountryPreset);
        profileBody.addView(countryPresetPanel.view());

        profileBody.addView(section("DNS"));
        profileBody.addView(label("DNS sets"));
        profileBody.addView(helper(
                "One set per line; comma-separate servers within a set. Randomisation selects one "
                        + "whole DNS set, not individual servers."));
        dns = field(true);
        dns.setHint("8.8.8.8, 8.8.4.4\n1.1.1.1, 1.0.0.1");
        profileBody.addView(dns);
        dnsStatus = helper("");
        profileBody.addView(dnsStatus);

        profileBody.addView(section("Selection"));
        randomize = check("Randomise identities and DNS sets", false);
        profileBody.addView(randomize);
        profileBody.addView(helper(
                "Global randomisation is stable per package: different scoped apps can receive "
                        + "different selections, while every process of one app stays consistent until Reroll."));

        profileBody.addView(section("Privacy"));
        hideVpn = check("Hide VPN transport/interface indicators", true);
        hideProxy = check("Hide HTTP/SOCKS proxy indicators", true);
        hideIpv6 = check("Suppress IPv6 addresses from covered APIs", false);
        profileBody.addView(hideVpn);
        profileBody.addView(hideProxy);
        profileBody.addView(hideIpv6);

        validationSummary = text("", 13);
        profileBody.addView(validationSummary);

        root.addView(section("Resolved preview"));
        preview = text("", 13);
        root.addView(preview);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button save = button("Save");
        reroll = button("Reroll");
        delete = button("Delete");
        buttons.addView(save);
        buttons.addView(reroll);
        buttons.addView(delete);
        root.addView(buttons);

        save.setOnClickListener(v -> saveSelected());
        reroll.setOnClickListener(v -> rerollSelected());
        delete.setOnClickListener(v -> confirmDeleteSelected());

        watch(dns);
        enabled.setOnCheckedChangeListener((buttonView, isChecked) -> validateAndPreview());
        randomize.setOnCheckedChangeListener((buttonView, isChecked) -> validateAndPreview());
        hideVpn.setOnCheckedChangeListener((buttonView, isChecked) -> validateAndPreview());
        hideProxy.setOnCheckedChangeListener((buttonView, isChecked) -> validateAndPreview());
        hideIpv6.setOnCheckedChangeListener((buttonView, isChecked) -> validateAndPreview());

        refreshTargets();
        selectTarget(ConfigKeys.GLOBAL);
        setContentView(scroll);
        scroll.requestApplyInsets();
    }

    private void applyCountryPreset(List<String> ipv4Values, boolean replace) {
        if (ipv4Values == null || ipv4Values.isEmpty()) return;
        if (replace) {
            identityEditors.clear();
            identitiesContainer.removeAllViews();
        } else {
            List<IdentityEditor> blanks = new ArrayList<>();
            for (IdentityEditor editor : identityEditors) if (editor.isBlank()) blanks.add(editor);
            for (IdentityEditor editor : blanks) {
                identityEditors.remove(editor);
                identitiesContainer.removeView(editor.container);
            }
        }

        Set<String> existing = new LinkedHashSet<>();
        for (IdentityEditor editor : identityEditors) {
            String raw = editor.ip.getText().toString().trim();
            if (Ipv4.isLiteral(raw)) existing.add(Ipv4.canonical(raw));
        }
        for (String raw : ipv4Values) {
            if (!Ipv4.isLiteral(raw)) continue;
            String canonical = Ipv4.canonical(raw);
            if (existing.add(canonical)) addIdentityEditor(NetworkIdentity.hidden(canonical));
        }
        if (identityEditors.isEmpty()) addIdentityEditor(null);
        refreshIdentityHeadings();
        validateAndPreview();
    }

    private void refreshTargets() {
        Map<String, TargetEntry> saved = new LinkedHashMap<>();
        Map<String, TargetEntry> installed = new LinkedHashMap<>();
        Set<String> index = prefs.getStringSet(ConfigKeys.INDEX, new LinkedHashSet<>());
        List<String> savedPackages = new ArrayList<>(index);
        savedPackages.sort(String::compareToIgnoreCase);
        for (String pkg : savedPackages) {
            if (MODULE_PACKAGE.equals(pkg)) continue;
            saved.put(pkg, new TargetEntry(pkg, appLabel(pkg), "Saved"));
        }

        try {
            PackageManager pm = getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> infos = pm.queryIntentActivities(
                    launcher, PackageManager.ResolveInfoFlags.of(0));
            for (ResolveInfo info : infos) {
                if (info.activityInfo == null || info.activityInfo.packageName == null) continue;
                String pkg = info.activityInfo.packageName;
                if (MODULE_PACKAGE.equals(pkg) || saved.containsKey(pkg)) continue;
                CharSequence label = info.loadLabel(pm);
                installed.put(pkg, new TargetEntry(pkg,
                        label == null ? pkg : label.toString(), "App"));
            }
        } catch (Throwable ignored) {
            // Saved/manual package names still remain fully usable.
        }

        List<TargetEntry> installedList = new ArrayList<>(installed.values());
        installedList.sort(Comparator.comparing(entry -> entry.label.toLowerCase(java.util.Locale.ROOT)));
        targets.clear();
        targets.add(TargetEntry.global());
        targets.addAll(saved.values());
        targets.addAll(installedList);

        ArrayAdapter<TargetEntry> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, targets);
        targetField.setAdapter(adapter);
        targetField.setOnItemClickListener((parent, view, position, id) -> {
            TargetEntry entry = (TargetEntry) parent.getItemAtPosition(position);
            selectTarget(entry.target);
        });
    }

    private void loadTargetFromField() {
        String raw = targetField.getText().toString().trim();
        for (TargetEntry entry : targets) {
            if (raw.equals(entry.toString()) || raw.equals(entry.target)) {
                selectTarget(entry.target);
                return;
            }
        }
        if (isPackageName(raw)) {
            selectTarget(raw);
            return;
        }
        targetField.setError("Choose a listed profile or enter a valid package such as org.example.app");
        targetField.requestFocus();
    }

    private void selectTarget(String target) {
        selectedTarget = target;
        editorLoadedFor = null;
        TargetEntry known = findTarget(target);
        targetField.setError(null);
        targetField.setText(known == null ? target : known.toString(), false);
        loadSelectedTarget();
    }

    private TargetEntry findTarget(String target) {
        for (TargetEntry entry : targets) if (entry.target.equals(target)) return entry;
        return null;
    }

    private void loadSelectedTarget() {
        loading = true;
        boolean global = isGlobal();
        policyContainer.setVisibility(global ? View.GONE : View.VISIBLE);
        if (global) {
            profileBody.setVisibility(View.VISIBLE);
            loadEditor(ConfigKeys.GLOBAL, Profile.hasStoredProfile(prefs, ConfigKeys.GLOBAL));
        } else {
            Profile.AppPolicy policy = Profile.appPolicy(prefs, selectedTarget);
            policySpinner.setSelection(policyIndex(policy));
            profileBody.setVisibility(policy == Profile.AppPolicy.CUSTOM ? View.VISIBLE : View.GONE);
            if (policy == Profile.AppPolicy.CUSTOM) {
                loadEditor(selectedTarget, Profile.hasStoredProfile(prefs, selectedTarget));
            }
        }
        loading = false;
        updateActionButtons();
        validateAndPreview();
    }

    private void onPolicyChanged() {
        if (isGlobal()) return;
        Profile.AppPolicy policy = selectedPolicy();
        profileBody.setVisibility(policy == Profile.AppPolicy.CUSTOM ? View.VISIBLE : View.GONE);
        if (policy == Profile.AppPolicy.CUSTOM && !selectedTarget.equals(editorLoadedFor)) {
            if (Profile.hasStoredProfile(prefs, selectedTarget)) {
                loadEditor(selectedTarget, true);
            } else if (Profile.hasStoredProfile(prefs, ConfigKeys.GLOBAL)) {
                loadProfileIntoEditor(Profile.load(prefs, ConfigKeys.GLOBAL), false);
                editorLoadedFor = selectedTarget;
            } else {
                loadProfileIntoEditor(null, false);
                editorLoadedFor = selectedTarget;
            }
        }
        updateActionButtons();
        validateAndPreview();
    }

    private void loadEditor(String target, boolean exists) {
        Profile profile = exists ? Profile.load(prefs, target) : null;
        loadProfileIntoEditor(profile, exists);
        editorLoadedFor = target;
    }

    private void loadProfileIntoEditor(Profile profile, boolean exists) {
        loading = true;
        if (profile == null) {
            enabled.setChecked(true);
            randomize.setChecked(false);
            hideVpn.setChecked(true);
            hideProxy.setChecked(true);
            hideIpv6.setChecked(false);
            currentSeed = random.nextLong();
            identityEditors.clear();
            identitiesContainer.removeAllViews();
            addIdentityEditor(null);
            dns.setText("");
        } else {
            enabled.setChecked(profile.enabled);
            randomize.setChecked(profile.randomize);
            hideVpn.setChecked(profile.hideVpn);
            hideProxy.setChecked(profile.hideProxy);
            hideIpv6.setChecked(profile.hideIpv6);
            currentSeed = exists ? profile.selectionSeed : random.nextLong();
            if (currentSeed == 0L && !exists) currentSeed = random.nextLong();
            identityEditors.clear();
            identitiesContainer.removeAllViews();
            if (profile.identities.isEmpty()) addIdentityEditor(null);
            else for (NetworkIdentity identity : profile.identities) addIdentityEditor(identity);
            dns.setText(formatDnsSets(profile.dnsSets));
        }
        loading = false;
        refreshIdentityHeadings();
    }

    private void saveSelected() {
        if (!resolveManualTargetIfNeeded()) return;
        if (!isGlobal()) {
            Profile.AppPolicy policy = selectedPolicy();
            if (policy != Profile.AppPolicy.CUSTOM) {
                Set<String> index = mutableIndex();
                index.add(selectedTarget);
                boolean ok = prefs.edit()
                        .putInt(ConfigKeys.SCHEMA_VERSION, ConfigKeys.CURRENT_SCHEMA_VERSION)
                        .putStringSet(ConfigKeys.INDEX, index)
                        .putString(ConfigKeys.p(selectedTarget, ConfigKeys.FIELD_POLICY), policy.storedValue)
                        .commit();
                if (!ok) {
                    toast("Save failed");
                    return;
                }
                refreshTargets();
                updateActionButtons();
                updatePreview();
                toast("Saved. Restart the target app to apply.");
                return;
            }
        }

        CollectedProfile collected = collectProfile(true);
        if (collected == null) return;
        String target = isGlobal() ? ConfigKeys.GLOBAL : selectedTarget;
        SharedPreferences.Editor editor = prefs.edit()
                .putInt(ConfigKeys.SCHEMA_VERSION, ConfigKeys.CURRENT_SCHEMA_VERSION)
                .putBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_ENABLED), enabled.isChecked())
                .putBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_RANDOMIZE), randomize.isChecked())
                .putBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_HIDE_VPN), hideVpn.isChecked())
                .putBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_HIDE_PROXY), hideProxy.isChecked())
                .putBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_HIDE_IPV6), hideIpv6.isChecked())
                .putLong(ConfigKeys.p(target, ConfigKeys.FIELD_SELECTION_SEED), currentSeed)
                .putString(ConfigKeys.p(target, ConfigKeys.FIELD_IDENTITIES),
                        NetworkIdentity.serializeList(collected.identities))
                .putString(ConfigKeys.p(target, ConfigKeys.FIELD_DNS), formatDnsSets(collected.dnsSets));

        if (!isGlobal()) {
            Set<String> index = mutableIndex();
            index.add(selectedTarget);
            editor.putStringSet(ConfigKeys.INDEX, index)
                    .putString(ConfigKeys.p(selectedTarget, ConfigKeys.FIELD_POLICY),
                            Profile.AppPolicy.CUSTOM.storedValue);
        }
        if (!editor.commit()) {
            toast("Save failed");
            return;
        }
        editorLoadedFor = target;
        refreshTargets();
        updateActionButtons();
        updatePreview();
        toast("Saved. Restart scoped app process(es) to apply.");
    }

    private boolean resolveManualTargetIfNeeded() {
        String raw = targetField.getText().toString().trim();
        TargetEntry current = findTarget(selectedTarget);
        if (current != null && raw.equals(current.toString())) return true;
        if (raw.equals(selectedTarget)
                && (ConfigKeys.GLOBAL.equals(selectedTarget) || isPackageName(selectedTarget))) {
            return true;
        }

        for (TargetEntry entry : targets) {
            if (raw.equals(entry.toString()) || raw.equals(entry.target)) {
                selectTarget(entry.target);
                toast("Target loaded. Review the profile, then press Save again.");
                return false;
            }
        }
        if (isPackageName(raw)) {
            selectTarget(raw);
            toast("Target loaded. Review the profile, then press Save again.");
            return false;
        }
        targetField.setError("Choose a listed profile or enter a valid package such as org.example.app");
        targetField.requestFocus();
        return false;
    }

    private CollectedProfile collectProfile(boolean focusInvalid) {
        List<NetworkIdentity> identities = new ArrayList<>();
        IdentityEditor firstInvalid = null;
        for (IdentityEditor editor : identityEditors) {
            if (editor.isBlank()) continue;
            NetworkIdentity.Validation validation = editor.validateNow();
            if (!validation.valid) {
                if (firstInvalid == null) firstInvalid = editor;
            } else {
                identities.add(validation.identity);
            }
        }
        DnsValidation dnsValidation = validateDns();
        boolean requiresData = enabled.isChecked();
        if (requiresData && identities.isEmpty() && firstInvalid == null) {
            validationSummary.setText("⚠ Add at least one valid network identity.");
            if (focusInvalid && !identityEditors.isEmpty()) identityEditors.get(0).focusIpv4();
            return null;
        }
        if (firstInvalid != null) {
            validationSummary.setText("⚠ Fix the highlighted network identity before saving.");
            if (focusInvalid) firstInvalid.focusFirstInvalid();
            return null;
        }
        if (!dnsValidation.valid && (requiresData || !dns.getText().toString().trim().isEmpty())) {
            validationSummary.setText("⚠ " + dnsValidation.error);
            if (focusInvalid) {
                dns.requestFocus();
                scrollTo(dns);
            }
            return null;
        }
        if (requiresData && dnsValidation.sets.isEmpty()) {
            validationSummary.setText("⚠ Add at least one valid DNS set.");
            if (focusInvalid) {
                dns.requestFocus();
                scrollTo(dns);
            }
            return null;
        }
        validationSummary.setText(requiresData ? "✓ Configuration is coherent." : "Profile is disabled; incomplete values may be saved.");
        return new CollectedProfile(identities, dnsValidation.sets);
    }

    private DnsValidation validateDns() {
        String raw = dns.getText().toString().trim();
        if (raw.isEmpty()) {
            dnsStatus.setText(enabled.isChecked() ? "⚠ Add at least one DNS set." : "DNS is optional while disabled.");
            return new DnsValidation(true, null, List.of());
        }
        int lineNumber = 0;
        for (String line : raw.split("[\\r\\n]+")) {
            lineNumber++;
            if (line.trim().isEmpty()) continue;
            for (String token : line.split(",")) {
                String value = token.trim();
                if (value.isEmpty() || !Ipv4.isLiteral(value)) {
                    String error = "DNS set " + lineNumber + " contains invalid IPv4 value: " + value;
                    dnsStatus.setText("⚠ " + error);
                    return new DnsValidation(false, error, List.of());
                }
            }
        }
        List<List<String>> sets = Profile.parseDnsSets(raw);
        dnsStatus.setText("✓ Parsed " + sets.size() + " DNS set" + (sets.size() == 1 ? "" : "s") + ".");
        return new DnsValidation(true, null, sets);
    }

    private void rerollSelected() {
        if (!isGlobal() && selectedPolicy() != Profile.AppPolicy.CUSTOM) {
            toast("Select Global or a Custom override to reroll.");
            return;
        }
        String target = isGlobal() ? ConfigKeys.GLOBAL : selectedTarget;
        if (!Profile.hasStoredProfile(prefs, target)) {
            toast("Save this profile first");
            return;
        }
        currentSeed = random.nextLong();
        if (!prefs.edit().putLong(ConfigKeys.p(target, ConfigKeys.FIELD_SELECTION_SEED), currentSeed).commit()) {
            toast("Reroll failed");
            return;
        }
        updatePreview();
        toast(isGlobal()
                ? "Global rerolled. Restart inheriting app processes."
                : "Rerolled. Restart the target app process(es).");
    }

    private void confirmDeleteSelected() {
        String title = isGlobal() ? "Reset Global profile?" : "Remove this app override?";
        String message = isGlobal()
                ? "This removes the Global configuration. Per-app Custom overrides are retained."
                : "This removes the saved mode/custom profile so the app returns to the Global default.";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(isGlobal() ? "Reset" : "Remove", (dialog, which) -> deleteSelected())
                .show();
    }

    private void deleteSelected() {
        String target = isGlobal() ? ConfigKeys.GLOBAL : selectedTarget;
        SharedPreferences.Editor editor = prefs.edit();
        clearProfileFields(editor, target);
        if (!isGlobal()) {
            editor.remove(ConfigKeys.p(selectedTarget, ConfigKeys.FIELD_POLICY));
            Set<String> index = mutableIndex();
            index.remove(selectedTarget);
            editor.putStringSet(ConfigKeys.INDEX, index);
        }
        if (!editor.commit()) {
            toast("Delete failed");
            return;
        }
        refreshTargets();
        loadSelectedTarget();
        toast(isGlobal() ? "Global profile reset" : "Override removed; app now inherits Global");
    }

    private static void clearProfileFields(SharedPreferences.Editor editor, String target) {
        for (String field : new String[]{
                ConfigKeys.FIELD_ENABLED, ConfigKeys.FIELD_RANDOMIZE, ConfigKeys.FIELD_HIDE_VPN,
                ConfigKeys.FIELD_HIDE_PROXY, ConfigKeys.FIELD_HIDE_IPV6,
                ConfigKeys.FIELD_SELECTION_SEED, ConfigKeys.FIELD_IDENTITIES, ConfigKeys.FIELD_DNS,
                ConfigKeys.LEGACY_PREFIX, ConfigKeys.LEGACY_IPV4, ConfigKeys.LEGACY_GATEWAYS}) {
            editor.remove(ConfigKeys.p(target, field));
        }
    }

    private void validateAndPreview() {
        if (loading) return;
        if (profileBody.getVisibility() == View.VISIBLE) collectProfile(false);
        updatePreview();
    }

    private void updatePreview() {
        if (!isGlobal()) {
            Profile.AppPolicy policy = selectedPolicy();
            if (policy == Profile.AppPolicy.DISABLED) {
                preview.setText(appNameLine() + "\nMode: Disabled for this app\nVector / LSPosed may still scope the process, but NetVeil installs no profile hooks.");
                return;
            }
            if (policy == Profile.AppPolicy.INHERIT_GLOBAL) {
                Profile.Resolved resolved = Profile.resolveEffective(prefs, selectedTarget);
                preview.setText(appNameLine() + "\nMode: Inherit Global\n" + resolvedText(resolved)
                        + "\n\nApplies only while this app is scoped in Vector / LSPosed.");
                return;
            }
        }

        CollectedProfile collected = collectProfile(false);
        if (collected == null) {
            preview.setText((isGlobal() ? "Global profile" : appNameLine())
                    + "\nFix the highlighted fields to produce a resolvable profile.");
            return;
        }
        Profile draft = Profile.create(enabled.isChecked(), randomize.isChecked(), hideVpn.isChecked(),
                hideProxy.isChecked(), hideIpv6.isChecked(), currentSeed,
                collected.identities, collected.dnsSets);
        if (!enabled.isChecked()) {
            preview.setText((isGlobal() ? "Global profile" : appNameLine()) + "\nProfile disabled.");
            return;
        }
        if (isGlobal() && randomize.isChecked()) {
            preview.setText("Global profile\n"
                    + collected.identities.size() + " network identit"
                    + (collected.identities.size() == 1 ? "y" : "ies") + "\n"
                    + collected.dnsSets.size() + " DNS set" + (collected.dnsSets.size() == 1 ? "" : "s") + "\n"
                    + "Per-app deterministic randomisation\n"
                    + privacyText()
                    + "\n\nEach scoped package derives its own stable selection from the Global seed until Reroll.");
            return;
        }
        Profile.Resolved resolved = draft.resolve();
        preview.setText((isGlobal() ? "Global profile" : appNameLine()) + "\n"
                + resolvedText(resolved) + "\n" + privacyText()
                + "\n\nApplies only to Vector / LSPosed-scoped processes.");
    }

    private String resolvedText(Profile.Resolved resolved) {
        if (resolved == null) return "Effective profile is disabled or incomplete.";
        String route = resolved.hasExplicitRoute()
                ? "IPv4: " + resolved.ipv4 + "/" + resolved.prefixLength
                        + "\nGateway: " + resolved.gateway
                : "IPv4: " + resolved.ipv4 + "\nGateway/routes: hidden";
        return route + "\nDNS: " + String.join(", ", resolved.dns);
    }

    private String privacyText() {
        return "VPN indicators: " + (hideVpn.isChecked() ? "hidden" : "preserved")
                + "\nProxy indicators: " + (hideProxy.isChecked() ? "hidden" : "preserved")
                + "\nIPv6: " + (hideIpv6.isChecked() ? "suppressed on covered APIs" : "preserved");
    }

    private String appNameLine() {
        TargetEntry entry = findTarget(selectedTarget);
        String label = entry == null ? selectedTarget : entry.label;
        return label + "\n" + selectedTarget;
    }

    private void updateActionButtons() {
        boolean editable = isGlobal() || selectedPolicy() == Profile.AppPolicy.CUSTOM;
        reroll.setEnabled(editable && Profile.hasStoredProfile(
                prefs, isGlobal() ? ConfigKeys.GLOBAL : selectedTarget));
        reroll.setText(isGlobal() ? "Reroll Global" : "Reroll");
        delete.setText(isGlobal() ? "Reset Global" : "Remove override");
        delete.setEnabled(isGlobal()
                ? Profile.hasStoredProfile(prefs, ConfigKeys.GLOBAL)
                : Profile.hasStoredProfile(prefs, selectedTarget)
                        || prefs.contains(ConfigKeys.p(selectedTarget, ConfigKeys.FIELD_POLICY)));
    }

    private void addIdentityEditor(NetworkIdentity identity) {
        IdentityEditor editor = new IdentityEditor(identity);
        identityEditors.add(editor);
        identitiesContainer.addView(editor.container);
        refreshIdentityHeadings();
    }

    private void removeIdentityEditor(IdentityEditor editor) {
        identityEditors.remove(editor);
        identitiesContainer.removeView(editor.container);
        if (identityEditors.isEmpty()) addIdentityEditor(null);
        refreshIdentityHeadings();
        validateAndPreview();
    }

    private void refreshIdentityHeadings() {
        for (int i = 0; i < identityEditors.size(); i++) {
            identityEditors.get(i).heading.setText("Identity " + (i + 1));
        }
    }

    private Profile.AppPolicy selectedPolicy() {
        return switch (policySpinner.getSelectedItemPosition()) {
            case 1 -> Profile.AppPolicy.CUSTOM;
            case 2 -> Profile.AppPolicy.DISABLED;
            default -> Profile.AppPolicy.INHERIT_GLOBAL;
        };
    }

    private static int policyIndex(Profile.AppPolicy policy) {
        return switch (policy) {
            case CUSTOM -> 1;
            case DISABLED -> 2;
            case INHERIT_GLOBAL -> 0;
        };
    }

    private boolean isGlobal() {
        return ConfigKeys.GLOBAL.equals(selectedTarget);
    }

    private Set<String> mutableIndex() {
        return new LinkedHashSet<>(prefs.getStringSet(ConfigKeys.INDEX, new LinkedHashSet<>()));
    }

    private String appLabel(String pkg) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(
                    pkg, PackageManager.ApplicationInfoFlags.of(0));
            CharSequence label = getPackageManager().getApplicationLabel(info);
            return label == null ? pkg : label.toString();
        } catch (Throwable ignored) {
            return pkg;
        }
    }

    private static boolean isPackageName(String value) {
        return value != null && value.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+");
    }

    private static String formatDnsSets(List<List<String>> sets) {
        List<String> lines = new ArrayList<>();
        for (List<String> set : sets) lines.add(String.join(", ", set));
        return String.join("\n", lines);
    }

    private void watch(EditText field) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                validateAndPreview();
            }
        });
    }

    private void scrollTo(View view) {
        scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, view.getTop() - dp(24))));
    }

    private TextView section(String value) {
        TextView view = text(value, 18);
        view.setPadding(0, dp(18), 0, dp(6));
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value, 14);
        view.setPadding(0, dp(8), 0, 0);
        return view;
    }

    private TextView helper(String value) {
        TextView view = text(value, 12);
        view.setPadding(0, dp(2), 0, dp(6));
        return view;
    }

    private EditText field(boolean multiLine) {
        EditText editText = new EditText(this);
        if (multiLine) {
            editText.setMinLines(2);
            editText.setGravity(Gravity.TOP);
        } else {
            editText.setSingleLine(true);
        }
        editText.setLayoutParams(matchWrap());
        return editText;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setLayoutParams(matchWrap());
        return spinner;
    }

    private CheckBox check(String label, boolean checked) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(label);
        checkBox.setChecked(checked);
        return checkBox;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return button;
    }

    private Button fullButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setLayoutParams(matchWrap());
        return button;
    }

    private TextView text(String value, float size) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(size);
        textView.setPadding(0, dp(6), 0, dp(6));
        return textView;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    private final class IdentityEditor {
        final LinearLayout container = new LinearLayout(MainActivity.this);
        final TextView heading;
        final EditText ip;
        final Spinner routeMode;
        final LinearLayout explicitFields = new LinearLayout(MainActivity.this);
        final EditText prefix;
        final EditText gateway;
        final TextView status;

        IdentityEditor(NetworkIdentity identity) {
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(dp(10), dp(8), dp(10), dp(12));
            heading = text("Identity", 16);
            container.addView(heading);
            container.addView(label("IPv4 address"));
            container.addView(helper("The IPv4 value exposed to the target app."));
            ip = field(false);
            ip.setInputType(InputType.TYPE_CLASS_PHONE);
            ip.setHint("202.128.115.2");
            container.addView(ip);

            container.addView(label("Gateway & route visibility"));
            routeMode = spinner(ROUTE_LABELS);
            container.addView(routeMode);
            container.addView(helper(
                    "Hide is recommended for arbitrary identities. Explicit mode is for a coherent virtual LAN."));

            explicitFields.setOrientation(LinearLayout.VERTICAL);
            explicitFields.addView(label("IPv4 prefix length"));
            explicitFields.addView(helper("0–32. /24 is common; /0 covers the entire IPv4 address space."));
            prefix = field(false);
            prefix.setInputType(InputType.TYPE_CLASS_NUMBER);
            prefix.setText("24");
            explicitFields.addView(prefix);
            explicitFields.addView(label("Gateway"));
            explicitFields.addView(helper("Must be different from the client IPv4 and inside the configured subnet."));
            gateway = field(false);
            gateway.setInputType(InputType.TYPE_CLASS_PHONE);
            gateway.setHint("192.168.1.1");
            explicitFields.addView(gateway);
            container.addView(explicitFields);

            status = helper("");
            container.addView(status);
            Button remove = fullButton("Remove identity");
            remove.setOnClickListener(v -> removeIdentityEditor(this));
            container.addView(remove);

            routeMode.setOnItemSelectedListener(new SimpleSelectionListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    explicitFields.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
                    if (!loading) validateAndPreview();
                }
            });
            watch(ip);
            watch(prefix);
            watch(gateway);

            if (identity != null) {
                ip.setText(identity.ipv4);
                if (identity.routeMode == NetworkIdentity.RouteMode.EXPLICIT) {
                    routeMode.setSelection(1);
                    prefix.setText(String.valueOf(identity.prefixLength));
                    gateway.setText(identity.gateway);
                } else {
                    routeMode.setSelection(0);
                }
            } else {
                routeMode.setSelection(0);
            }
            explicitFields.setVisibility(routeMode.getSelectedItemPosition() == 1 ? View.VISIBLE : View.GONE);
            validateNow();
        }

        boolean isBlank() {
            return ip.getText().toString().trim().isEmpty()
                    && (routeMode.getSelectedItemPosition() == 0
                    || gateway.getText().toString().trim().isEmpty());
        }

        NetworkIdentity.Validation validateNow() {
            NetworkIdentity.RouteMode mode = routeMode.getSelectedItemPosition() == 1
                    ? NetworkIdentity.RouteMode.EXPLICIT : NetworkIdentity.RouteMode.HIDDEN;
            NetworkIdentity.Validation validation = NetworkIdentity.validate(
                    ip.getText().toString(), mode,
                    prefix.getText().toString(), gateway.getText().toString());
            if (isBlank()) {
                status.setText("Enter an IPv4 address, or remove this identity.");
            } else if (!validation.valid) {
                status.setText("⚠ " + validation.error);
            } else if (validation.warning != null) {
                status.setText("⚠ " + validation.warning);
            } else {
                status.setText("✓ Valid identity.");
            }
            return validation;
        }

        void focusIpv4() {
            ip.requestFocus();
            scrollTo(container);
        }

        void focusFirstInvalid() {
            if (!Ipv4.isLiteral(ip.getText().toString())) {
                ip.requestFocus();
            } else if (routeMode.getSelectedItemPosition() == 1) {
                int value = -1;
                try {
                    value = Integer.parseInt(prefix.getText().toString().trim());
                } catch (NumberFormatException ignored) {
                    // Prefix field is the first invalid advanced value.
                }
                if (value < 0 || value > 32) prefix.requestFocus();
                else gateway.requestFocus();
            } else {
                ip.requestFocus();
            }
            scrollTo(container);
        }
    }

    private static final class TargetEntry {
        final String target;
        final String label;
        final String group;

        TargetEntry(String target, String label, String group) {
            this.target = target;
            this.label = label;
            this.group = group;
        }

        static TargetEntry global() {
            return new TargetEntry(ConfigKeys.GLOBAL, GLOBAL_LABEL, "");
        }

        @Override public String toString() {
            if (ConfigKeys.GLOBAL.equals(target)) return GLOBAL_LABEL;
            return group + " · " + label + " — " + target;
        }
    }

    private abstract static class SimpleSelectionListener implements AdapterView.OnItemSelectedListener {
        @Override public void onNothingSelected(AdapterView<?> parent) {}
    }

    private static final class DnsValidation {
        final boolean valid;
        final String error;
        final List<List<String>> sets;

        DnsValidation(boolean valid, String error, List<List<String>> sets) {
            this.valid = valid;
            this.error = error;
            this.sets = sets;
        }
    }

    private static final class CollectedProfile {
        final List<NetworkIdentity> identities;
        final List<List<String>> dnsSets;

        CollectedProfile(List<NetworkIdentity> identities, List<List<String>> dnsSets) {
            this.identities = identities;
            this.dnsSets = dnsSets;
        }
    }
}
