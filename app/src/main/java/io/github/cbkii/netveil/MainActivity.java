package io.github.cbkii.netveil;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import io.github.cbkii.netveil.config.BasicProfileMetadata;
import io.github.cbkii.netveil.config.BasicProfileState;
import io.github.cbkii.netveil.config.ConfigKeys;
import io.github.cbkii.netveil.config.DnsPresetProvider;
import io.github.cbkii.netveil.config.Ipv4;
import io.github.cbkii.netveil.config.NetworkIdentity;
import io.github.cbkii.netveil.config.Profile;
import io.github.cbkii.netveil.config.ProfilePersistence;
import io.github.cbkii.netveil.config.RecommendedProfileFactory;
import io.github.cbkii.netveil.country.CountryCatalog;
import io.github.cbkii.netveil.country.CountryPack;
import io.github.cbkii.netveil.country.CountryPackStore;
import io.github.cbkii.netveil.country.CountryPresetPanel;
import io.github.cbkii.netveil.country.CountryRefreshScheduler;
import io.github.cbkii.netveil.ui.UiFactory;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lightweight platform-only configuration UI. Vector/LSPosed remains the outer scope gate. */
public final class MainActivity extends Activity {
    private static final String MODULE_PACKAGE = "dev.ip.netveil";
    private static final String GLOBAL_LABEL = "★ All scoped apps (Global)";
    private static final String UI_PREFS = "ui_state";
    private static final String UI_BASIC_COUNTRY = "basic_country";
    private static final String UI_BASIC_OVERRIDES = "basic_overrides";
    private static final long BASIC_REFRESH_STALE_MILLIS = 30L * 24L * 60L * 60L * 1000L;
    private static final String[] MODE_LABELS = {"Basic", "Advanced"};
    private static final String[] POLICY_LABELS = {
            "Use Global", "Custom", "Off for this app"
    };
    private static final String[] ROUTE_LABELS = {
            "Omit gateway & routes", "Explicit virtual network"
    };

    private final SecureRandom random = new SecureRandom();
    private final List<TargetEntry> targets = new ArrayList<>();
    private final List<IdentityEditor> identityEditors = new ArrayList<>();

    private SharedPreferences prefs;
    private SharedPreferences uiPrefs;
    private UiFactory ui;
    private ScrollView scroll;
    private LinearLayout root;
    private LinearLayout basicContainer;
    private LinearLayout advancedContainer;
    private RadioGroup modeChoices;

    private Spinner basicCountry;
    private Switch basicOverrides;
    private TextView basicDataStatus;
    private TextView basicStateStatus;
    private Button basicApply;
    private Button basicRefreshReplace;
    private CountryPackStore.Loaded basicLoaded;
    private RecommendedProfileFactory.Draft basicDraft;
    private long basicDraftSeed;
    private boolean basicRefreshRunning;

    private AutoCompleteTextView targetField;
    private LinearLayout policyContainer;
    private RadioGroup policyChoices;
    private LinearLayout profileBody;
    private Switch enabled;
    private Switch randomize;
    private Switch hideVpn;
    private Switch hideProxy;
    private Switch hideIpv6;
    private LinearLayout identitiesContainer;
    private EditText dns;
    private TextView dnsStatus;
    private TextView validationSummary;
    private TextView summaryChip;
    private TextView preview;
    private Button reroll;
    private Button clearProfile;
    private CountryPresetPanel countryPresetPanel;

    private String selectedTarget = ConfigKeys.GLOBAL;
    private String editorLoadedFor;
    private String editorBaseline;
    private long currentSeed;
    private boolean loading = true;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(ConfigKeys.PREFS, MODE_PRIVATE);
        uiPrefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
        boolean profileSchemaReset = !prefs.getAll().isEmpty() && !Profile.hasCurrentSchema(prefs);
        if (!Profile.ensureCurrentSchema(prefs)) {
            Toast.makeText(this, "Unable to initialise NetVeil profile storage.", Toast.LENGTH_LONG)
                    .show();
            finish();
            return;
        }

        ui = new UiFactory(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ui.color(R.color.nv_background));

        root = ui.vertical();
        int pad = ui.dp(18);
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

        buildHeader();
        buildModeSelector();

        basicContainer = ui.vertical();
        basicContainer.setLayoutParams(ui.matchWrap());
        root.addView(basicContainer);
        buildBasicView();

        advancedContainer = ui.vertical();
        advancedContainer.setLayoutParams(ui.matchWrap());
        root.addView(advancedContainer);
        buildProfileCard();

        profileBody = ui.vertical();
        profileBody.setLayoutParams(ui.matchWrap());
        advancedContainer.addView(profileBody);
        buildProfileStatusCard();
        buildIdentityCard();
        buildCountryCard();
        buildDnsCard();
        buildSelectionCard();
        buildPrivacyCard();

        validationSummary = ui.status("", UiFactory.Tone.NEUTRAL);
        validationSummary.setLayoutParams(ui.blockParams(16));
        profileBody.addView(validationSummary);

        buildSummaryCard();
        buildActionsCard();

        watch(dns);
        enabled.setOnCheckedChangeListener((buttonView, isChecked) -> validateAndPreview());
        randomize.setOnCheckedChangeListener((buttonView, isChecked) -> validateAndPreview());
        hideVpn.setOnCheckedChangeListener((buttonView, isChecked) -> validateAndPreview());
        hideProxy.setOnCheckedChangeListener((buttonView, isChecked) -> validateAndPreview());
        hideIpv6.setOnCheckedChangeListener((buttonView, isChecked) -> validateAndPreview());

        refreshTargets();
        selectTargetNow(ConfigKeys.GLOBAL);
        loadBasicLocal();
        setMode(0);
        loading = false;

        modeChoices.setOnCheckedChangeListener((group, checkedId) -> setMode(
                ui.choiceIndex(modeChoices)));

        setContentView(scroll);
        scroll.requestApplyInsets();
        maybeRefreshBasicDataInBackground();

        if (profileSchemaReset) {
            Toast.makeText(this,
                    "Previous profile configuration was cleared for the current NetVeil format.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void buildHeader() {
        LinearLayout header = ui.row();
        header.setLayoutParams(ui.blockParams(12));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(ui.dp(64), ui.dp(64));
        iconParams.rightMargin = ui.dp(14);
        header.addView(icon, iconParams);

        LinearLayout titles = ui.vertical();
        titles.addView(ui.appTitle("NetVeil"));
        titles.addView(ui.helper("App-visible network identity masking"));
        header.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header);

        TextView publicIpNotice = ui.status(
                "Changes affect app-visible Android network APIs only. NetVeil does not change "
                        + "your real/public IP.",
                UiFactory.Tone.INFO);
        publicIpNotice.setLayoutParams(ui.blockParams(8));
        root.addView(publicIpNotice);

        TextView scopeNotice = ui.helper(
                "Vector / LSPosed scope remains the execution gate. Global applies only to apps "
                        + "already scoped there; NetVeil does not broaden framework scope.");
        scopeNotice.setLayoutParams(ui.blockParams(12));
        root.addView(scopeNotice);
    }

    private void buildModeSelector() {
        LinearLayout card = ui.card();
        card.addView(ui.sectionTitle("Configuration"));
        modeChoices = ui.choiceGroup(MODE_LABELS, false);
        ui.setChoice(modeChoices, 0);
        card.addView(modeChoices);
        root.addView(card);
    }

    private void setMode(int mode) {
        if (basicContainer == null || advancedContainer == null) return;
        boolean basic = mode == 0;
        basicContainer.setVisibility(basic ? View.VISIBLE : View.GONE);
        advancedContainer.setVisibility(basic ? View.GONE : View.VISIBLE);
    }

    private void buildBasicView() {
        LinearLayout card = ui.card();
        card.addView(ui.sectionTitle("Global setup"));
        card.addView(ui.helper(
                "Choose a country, then Apply to save the Global profile. For custom values, use "
                        + "Advanced and save there."));

        card.addView(ui.label("Country"));
        basicCountry = ui.spinner(CountryCatalog.labels());
        String savedCountry = uiPrefs.getString(UI_BASIC_COUNTRY, "");
        String initialCountry = CountryCatalog.supports(savedCountry)
                ? savedCountry : CountryCatalog.defaultForLocale(Locale.getDefault());
        basicCountry.setSelection(Math.max(0, CountryCatalog.indexOf(initialCountry)));
        card.addView(basicCountry);

        basicDataStatus = ui.status("", UiFactory.Tone.INFO);
        LinearLayout.LayoutParams dataParams = ui.blockParams(8);
        dataParams.topMargin = ui.dp(8);
        basicDataStatus.setLayoutParams(dataParams);
        card.addView(basicDataStatus);

        basicOverrides = ui.switchControl(
                "Allow Basic to replace Advanced Global",
                uiPrefs.getBoolean(UI_BASIC_OVERRIDES, false));
        card.addView(basicOverrides);
        card.addView(ui.helper(
                "Off protects an Advanced-customised Global profile. Replacement still requires an "
                        + "explicit Apply/Update/Refresh action."));

        basicStateStatus = ui.status("", UiFactory.Tone.NEUTRAL);
        basicStateStatus.setLayoutParams(ui.blockParams(12));
        card.addView(basicStateStatus);

        basicApply = ui.button("Apply Global profile", UiFactory.ButtonKind.PRIMARY);
        basicApply.setLayoutParams(ui.matchWrap());
        card.addView(basicApply);

        basicRefreshReplace = ui.button("Refresh & replace Global", UiFactory.ButtonKind.TONAL);
        LinearLayout.LayoutParams refreshParams = ui.matchWrap();
        refreshParams.topMargin = ui.dp(10);
        basicRefreshReplace.setLayoutParams(refreshParams);
        card.addView(basicRefreshReplace);

        basicContainer.addView(card);

        basicCountry.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                    int position, long id) {
                String country = CountryCatalog.codeAt(position);
                uiPrefs.edit().putString(UI_BASIC_COUNTRY, country).apply();
                rebuildBasicDraft();
            }

            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        basicOverrides.setOnCheckedChangeListener((buttonView, checked) -> {
            uiPrefs.edit().putBoolean(UI_BASIC_OVERRIDES, checked).apply();
            renderBasicState();
        });
        basicApply.setOnClickListener(v -> requestApplyBasic());
        basicRefreshReplace.setOnClickListener(v -> requestManualBasicRefresh());
    }

    private void loadBasicLocal() {
        try {
            basicLoaded = CountryPackStore.loadBest(this);
            rebuildBasicDraft();
        } catch (Exception e) {
            basicLoaded = null;
            basicDraft = null;
            ui.setStatus(basicDataStatus, UiFactory.Tone.ERROR,
                    "Country data unavailable: " + safeMessage(e));
            renderBasicState();
        }
    }

    private void rebuildBasicDraft() {
        if (basicLoaded == null || basicCountry == null) return;
        String country = selectedBasicCountry();
        long seed = basicSeedForDraft();
        try {
            basicDraft = RecommendedProfileFactory.create(country, basicLoaded.pack, seed);
            int count = basicDraft.profile.identities.size();
            int dnsCount = basicDraft.profile.dnsSets.size();
            String data = CountryCatalog.labelFor(country)
                    + " · " + count + " high-confidence identities · " + dnsCount + " DNS sets"
                    + "\nData: " + basicLoaded.pack.generatedAt
                    + " · " + basicLoaded.source.label;
            CountryPackStore.Outcome outcome = CountryRefreshScheduler.lastOutcome(this);
            if (outcome == CountryPackStore.Outcome.FAILED) {
                String error = CountryRefreshScheduler.lastError(this);
                data += "\nLast refresh failed; valid local data retained"
                        + (error == null || error.isBlank() ? "" : ": " + error);
                ui.setStatus(basicDataStatus, UiFactory.Tone.WARNING, data);
            } else {
                ui.setStatus(basicDataStatus, UiFactory.Tone.INFO, data);
            }
            syncBasicDraftIntoFreshAdvanced();
        } catch (IllegalArgumentException e) {
            basicDraft = null;
            ui.setStatus(basicDataStatus, UiFactory.Tone.ERROR, e.getMessage());
        }
        renderBasicState();
    }

    private long basicSeedForDraft() {
        if (BasicProfileMetadata.isManaged(prefs)
                && Profile.hasStoredProfile(prefs, ConfigKeys.GLOBAL)) {
            Profile stored = Profile.load(prefs, ConfigKeys.GLOBAL);
            if (stored != null && stored.selectionSeed != 0L) return stored.selectionSeed;
        }
        if (basicDraftSeed == 0L) basicDraftSeed = nonZeroRandom();
        return basicDraftSeed;
    }

    private void syncBasicDraftIntoFreshAdvanced() {
        if (basicDraft == null || !ConfigKeys.GLOBAL.equals(selectedTarget)) return;
        if (Profile.hasStoredProfile(prefs, ConfigKeys.GLOBAL)) return;
        if (editorBaseline != null && hasUnsavedChanges()) return;
        loadProfileIntoEditor(basicDraft.profile, false);
        editorLoadedFor = ConfigKeys.GLOBAL;
        markEditorClean();
        validateAndPreview();
    }

    private BasicProfileState.Kind currentBasicState() {
        boolean stored = Profile.hasStoredProfile(prefs, ConfigKeys.GLOBAL);
        Profile profile = stored ? Profile.load(prefs, ConfigKeys.GLOBAL) : null;
        boolean resolvable = profile != null && profile.resolve() != null;
        String currentFingerprint = basicDraft == null ? null : basicDraft.fingerprint;
        return BasicProfileState.classify(
                stored,
                resolvable,
                BasicProfileMetadata.isManaged(prefs),
                BasicProfileMetadata.fingerprint(prefs),
                currentFingerprint);
    }

    private void renderBasicState() {
        if (basicApply == null || basicRefreshReplace == null) return;
        boolean draftReady = basicDraft != null;
        BasicProfileState.Kind state = currentBasicState();
        String country = basicDraft == null ? selectedBasicCountry() : basicDraft.countryCode;
        String label = CountryCatalog.labelFor(country);
        boolean replacementAllowed = state != BasicProfileState.Kind.ADVANCED_CUSTOM
                || basicOverrides.isChecked();

        switch (state) {
            case ABSENT -> {
                ui.setStatus(basicStateStatus, UiFactory.Tone.INFO,
                        "Recommended Global draft ready · " + label + " · not saved yet");
                basicApply.setText("Apply Global profile");
                basicApply.setEnabled(draftReady);
            }
            case ACTIVE -> {
                ui.setStatus(basicStateStatus, UiFactory.Tone.SUCCESS,
                        "Global profile active · " + CountryCatalog.labelFor(
                                BasicProfileMetadata.country(prefs)));
                basicApply.setText("Global profile active");
                basicApply.setEnabled(false);
            }
            case UPDATE_AVAILABLE -> {
                ui.setStatus(basicStateStatus, UiFactory.Tone.WARNING,
                        "Recommended update available · " + label);
                basicApply.setText("Update Global profile");
                basicApply.setEnabled(draftReady);
            }
            case ADVANCED_CUSTOM -> {
                ui.setStatus(basicStateStatus, UiFactory.Tone.INFO,
                        basicOverrides.isChecked()
                                ? "Custom Advanced Global active · explicit Basic replacement allowed"
                                : "Custom Advanced Global configuration active");
                basicApply.setText("Replace with recommended " + label);
                basicApply.setEnabled(draftReady && replacementAllowed);
            }
            case INVALID -> {
                ui.setStatus(basicStateStatus, UiFactory.Tone.WARNING,
                        "Saved Global profile is incomplete · recommended replacement ready");
                basicApply.setText("Replace Global profile");
                basicApply.setEnabled(draftReady);
            }
        }
        basicApply.setAlpha(basicApply.isEnabled() ? 1f : 0.45f);
        basicRefreshReplace.setEnabled(draftReady && replacementAllowed && !basicRefreshRunning);
        basicRefreshReplace.setAlpha(basicRefreshReplace.isEnabled() ? 1f : 0.45f);
    }

    private void requestApplyBasic() {
        if (basicDraft == null) return;
        BasicProfileState.Kind state = currentBasicState();
        if (state == BasicProfileState.Kind.ADVANCED_CUSTOM && !basicOverrides.isChecked()) {
            ui.setStatus(basicStateStatus, UiFactory.Tone.WARNING,
                    "Advanced Global is protected. Enable the replacement toggle or edit it in Advanced.");
            return;
        }
        runAfterDiscardIfNeeded(() -> persistBasicDraft(basicDraft, "Global profile saved"));
    }

    private void persistBasicDraft(RecommendedProfileFactory.Draft draft, String successMessage) {
        if (draft == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        ProfilePersistence.putProfile(editor, ConfigKeys.GLOBAL, draft.profile);
        BasicProfileMetadata.markBasic(editor, draft);
        if (!editor.commit()) {
            toast("Save failed");
            return;
        }
        basicDraftSeed = draft.profile.selectionSeed;
        loadSelectedTarget();
        rebuildBasicDraft();
        toast(successMessage + ". Restart scoped app process(es) to apply.");
    }

    private void requestManualBasicRefresh() {
        if (basicRefreshRunning) return;
        BasicProfileState.Kind state = currentBasicState();
        if (state == BasicProfileState.Kind.ADVANCED_CUSTOM && !basicOverrides.isChecked()) {
            ui.setStatus(basicStateStatus, UiFactory.Tone.WARNING,
                    "Advanced Global is protected. Enable the replacement toggle before replacing it.");
            return;
        }
        runAfterDiscardIfNeeded(() -> startManualBasicRefresh(selectedBasicCountry()));
    }

    private void startManualBasicRefresh(String country) {
        if (basicRefreshRunning) return;
        basicRefreshRunning = true;
        basicCountry.setEnabled(false);
        basicApply.setEnabled(false);
        basicRefreshReplace.setEnabled(false);
        ui.setStatus(basicDataStatus, UiFactory.Tone.INFO,
                "Refreshing validated country data…");
        long seed = basicSeedForDraft();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            CountryPackStore.RefreshResult result = CountryPackStore.refreshBlocking(this);
            CountryRefreshScheduler.recordRefreshResult(this, result);
            RecommendedProfileFactory.Draft refreshedDraft = null;
            String error = result.error;
            if (result.outcome != CountryPackStore.Outcome.FAILED) {
                try {
                    CountryPackStore.Loaded loaded = CountryPackStore.loadBest(this);
                    refreshedDraft = RecommendedProfileFactory.create(country, loaded.pack, seed);
                } catch (Exception e) {
                    error = safeMessage(e);
                }
            }
            RecommendedProfileFactory.Draft finalDraft = refreshedDraft;
            String finalError = error;
            if (!isDestroyed()) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    basicRefreshRunning = false;
                    basicCountry.setEnabled(true);
                    loadBasicLocal();
                    if (result.outcome == CountryPackStore.Outcome.FAILED || finalDraft == null) {
                        ui.setStatus(basicDataStatus, UiFactory.Tone.WARNING,
                                "Refresh failed; saved Global was not changed. "
                                        + (finalError == null ? "" : finalError));
                        renderBasicState();
                    } else {
                        basicDraft = finalDraft;
                        persistBasicDraft(finalDraft,
                                result.outcome == CountryPackStore.Outcome.UPDATED
                                        ? "Country data refreshed and Global replaced"
                                        : "Country data already current; Global replaced");
                    }
                });
            }
            executor.shutdown();
        });
    }

    private void maybeRefreshBasicDataInBackground() {
        long lastCheck = CountryRefreshScheduler.lastCheckMillis(this);
        long now = System.currentTimeMillis();
        if (basicRefreshRunning || (lastCheck > 0L && now - lastCheck < BASIC_REFRESH_STALE_MILLIS)) {
            return;
        }
        basicRefreshRunning = true;
        renderBasicState();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            CountryPackStore.RefreshResult result = CountryPackStore.refreshBlocking(this);
            CountryRefreshScheduler.recordRefreshResult(this, result);
            if (!isDestroyed()) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    basicRefreshRunning = false;
                    loadBasicLocal();
                    renderBasicState();
                });
            }
            executor.shutdown();
        });
    }

    private String selectedBasicCountry() {
        return CountryCatalog.codeAt(Math.max(0, basicCountry.getSelectedItemPosition()));
    }

    private void buildProfileCard() {
        LinearLayout card = ui.card();
        card.addView(ui.sectionTitle("Target profile"));
        card.addView(ui.label("Profile"));

        targetField = new AutoCompleteTextView(this);
        ui.styleInput(targetField, false);
        targetField.setThreshold(0);
        targetField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        targetField.setHint("Choose Global/app, or type a package name");
        targetField.setDropDownBackgroundDrawable(
                new ColorDrawable(ui.color(R.color.nv_surface_container_high)));
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
        card.addView(targetField);
        card.addView(ui.helper(
                "Choose Global, a saved or launchable app, or enter an exact package name manually."));

        Button loadTarget = ui.button("Load selected profile", UiFactory.ButtonKind.TONAL);
        loadTarget.setLayoutParams(ui.matchWrap());
        loadTarget.setOnClickListener(v -> loadTargetFromField());
        card.addView(loadTarget);

        clearProfile = ui.button("Clear selected profile", UiFactory.ButtonKind.ERROR);
        LinearLayout.LayoutParams clearParams = ui.matchWrap();
        clearParams.topMargin = ui.dp(10);
        clearProfile.setLayoutParams(clearParams);
        clearProfile.setOnClickListener(v -> requestClearSelected());
        card.addView(clearProfile);

        policyContainer = ui.vertical();
        policyContainer.addView(ui.divider());
        policyContainer.addView(ui.subheading("Per-app mode"));
        policyContainer.addView(ui.helper(
                "Use Global applies the Global profile. Custom stores separate settings. Off for "
                        + "this app installs no NetVeil profile hooks even if Vector / LSPosed still scopes it."));

        policyChoices = ui.choiceGroup(POLICY_LABELS, true);
        policyContainer.addView(policyChoices);
        policyChoices.setOnCheckedChangeListener((group, checkedId) -> {
            if (!loading) onPolicyChanged();
        });
        card.addView(policyContainer);
        advancedContainer.addView(card);
    }

    private void buildProfileStatusCard() {
        LinearLayout card = ui.card();
        card.addView(ui.sectionTitle("Profile status"));
        enabled = ui.switchControl("Enable masking for this profile", true);
        card.addView(enabled);
        card.addView(ui.helper(
                "When off, the profile remains editable and may retain incomplete values, but its "
                        + "masking settings are not active."));
        profileBody.addView(card);
    }

    private void buildIdentityCard() {
        LinearLayout card = ui.card();
        card.addView(ui.sectionTitle("Network identities"));
        card.addView(ui.helper(
                "Each entry is a complete app-visible identity. Omit gateway & routes is the normal "
                        + "choice for arbitrary IPv4 values."));

        identitiesContainer = ui.vertical();
        identitiesContainer.setLayoutParams(ui.matchWrap());
        card.addView(identitiesContainer);

        Button addIdentity = ui.button("+ Add network identity", UiFactory.ButtonKind.OUTLINE);
        addIdentity.setLayoutParams(ui.matchWrap());
        addIdentity.setOnClickListener(v -> {
            addIdentityEditor(null);
            validateAndPreview();
        });
        card.addView(addIdentity);
        profileBody.addView(card);
    }

    private void buildCountryCard() {
        LinearLayout card = ui.card();
        card.addView(ui.sectionTitle("Country IPv4 preset"));
        countryPresetPanel = new CountryPresetPanel(this, this::applyCountryPreset);
        card.addView(countryPresetPanel.view());
        profileBody.addView(card);
    }

    private void buildDnsCard() {
        LinearLayout card = ui.card();
        card.addView(ui.sectionTitle("DNS"));
        card.addView(ui.label("DNS sets"));
        card.addView(ui.helper(
                "One set per line; comma-separate servers within a set. Randomisation selects one "
                        + "whole DNS set rather than mixing individual servers."));

        Button populateDns = ui.button("Populate recommended DNS", UiFactory.ButtonKind.TONAL);
        populateDns.setLayoutParams(ui.matchWrap());
        populateDns.setOnClickListener(v -> {
            String country = countryPresetPanel.selectedCountryCode();
            dns.setText(DnsPresetProvider.format(country));
            toast("Recommended " + CountryCatalog.labelFor(country)
                    + " DNS sets added to the draft. Edit as needed, then Save.");
        });
        card.addView(populateDns);

        dns = ui.input(true);
        LinearLayout.LayoutParams dnsParams = ui.matchWrap();
        dnsParams.topMargin = ui.dp(10);
        dns.setLayoutParams(dnsParams);
        dns.setHint(DnsPresetProvider.format("AU"));
        card.addView(dns);

        dnsStatus = ui.status("", UiFactory.Tone.NEUTRAL);
        LinearLayout.LayoutParams statusParams = ui.blockParams(0);
        statusParams.topMargin = ui.dp(10);
        dnsStatus.setLayoutParams(statusParams);
        card.addView(dnsStatus);
        profileBody.addView(card);
    }

    private void buildSelectionCard() {
        LinearLayout card = ui.card();
        card.addView(ui.sectionTitle("Randomisation"));
        randomize = ui.switchControl("Randomise identities and DNS sets", false);
        card.addView(randomize);
        card.addView(ui.helper(
                "Selection remains stable for each package until Reroll. With Global, different "
                        + "scoped packages can receive different stable selections."));
        profileBody.addView(card);
    }

    private void buildPrivacyCard() {
        LinearLayout card = ui.card();
        card.addView(ui.sectionTitle("Privacy indicators"));

        hideVpn = ui.switchControl("Hide VPN indicators from apps", true);
        card.addView(hideVpn);
        card.addView(ui.helper("Does not disable or disconnect the real VPN."));

        hideProxy = ui.switchControl("Hide proxy indicators from apps", true);
        card.addView(hideProxy);
        card.addView(ui.helper("Does not change the device's real proxy configuration."));

        hideIpv6 = ui.switchControl("Suppress IPv6 addresses", false);
        card.addView(hideIpv6);
        card.addView(ui.helper("Removes IPv6 addresses from covered app-visible APIs."));
        profileBody.addView(card);
    }

    private void buildSummaryCard() {
        LinearLayout card = ui.card();
        LinearLayout heading = ui.row();
        TextView title = ui.sectionTitle("Effective state");
        heading.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        summaryChip = ui.chip("GLOBAL", UiFactory.Tone.INFO);
        heading.addView(summaryChip);
        card.addView(heading);

        preview = ui.body("");
        preview.setTextSize(15);
        preview.setLineSpacing(0, 1.14f);
        card.addView(preview);
        card.addView(ui.helper(
                "This summary reflects the loaded draft or resolved profile. Save, then restart the "
                        + "target app process to apply changes."));
        advancedContainer.addView(card);
    }

    private void buildActionsCard() {
        LinearLayout card = ui.card();
        card.addView(ui.sectionTitle("Apply changes"));

        Button save = ui.button("Save changes", UiFactory.ButtonKind.PRIMARY);
        save.setLayoutParams(ui.matchWrap());
        card.addView(save);

        reroll = ui.button("Reroll", UiFactory.ButtonKind.OUTLINE);
        LinearLayout.LayoutParams rerollParams = ui.matchWrap();
        rerollParams.topMargin = ui.dp(10);
        reroll.setLayoutParams(rerollParams);
        card.addView(reroll);

        save.setOnClickListener(v -> saveSelected());
        reroll.setOnClickListener(v -> rerollSelected());
        advancedContainer.addView(card);
    }

    private void applyCountryPreset(List<String> ipv4Values, boolean replace) {
        if (ipv4Values == null || ipv4Values.isEmpty()) return;
        if (replace) {
            identityEditors.clear();
            identitiesContainer.removeAllViews();
        } else {
            List<IdentityEditor> blanks = new ArrayList<>();
            for (IdentityEditor editor : identityEditors) {
                if (editor.isBlank()) blanks.add(editor);
            }
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
            // Saved/manual package names remain usable if package enumeration is unavailable.
        }

        List<TargetEntry> installedList = new ArrayList<>(installed.values());
        installedList.sort(Comparator.comparing(
                entry -> entry.label.toLowerCase(Locale.ROOT)));
        targets.clear();
        targets.add(TargetEntry.global());
        targets.addAll(saved.values());
        targets.addAll(installedList);

        ArrayAdapter<TargetEntry> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, targets);
        targetField.setAdapter(adapter);
        targetField.setOnItemClickListener((parent, view, position, id) -> {
            TargetEntry entry = (TargetEntry) parent.getItemAtPosition(position);
            requestSelectTarget(entry.target);
        });
    }

    private void loadTargetFromField() {
        String raw = targetField.getText().toString().trim();
        for (TargetEntry entry : targets) {
            if (raw.equals(entry.toString()) || raw.equals(entry.target)) {
                requestSelectTarget(entry.target);
                return;
            }
        }
        if (isPackageName(raw)) {
            requestSelectTarget(raw);
            return;
        }
        targetField.setError(
                "Choose a listed profile or enter a valid package such as org.example.app");
        targetField.requestFocus();
    }

    private void requestSelectTarget(String target) {
        runAfterDiscardIfNeeded(() -> selectTargetNow(target));
    }

    private void selectTargetNow(String target) {
        selectedTarget = target;
        editorLoadedFor = null;
        TargetEntry known = findTarget(target);
        targetField.setError(null);
        targetField.setText(known == null ? target : known.toString(), false);
        loadSelectedTarget();
    }

    private TargetEntry findTarget(String target) {
        for (TargetEntry entry : targets) {
            if (entry.target.equals(target)) return entry;
        }
        return null;
    }

    private void loadSelectedTarget() {
        boolean previousLoading = loading;
        loading = true;
        boolean global = isGlobal();
        policyContainer.setVisibility(global ? View.GONE : View.VISIBLE);
        if (global) {
            profileBody.setVisibility(View.VISIBLE);
            if (Profile.hasStoredProfile(prefs, ConfigKeys.GLOBAL)) {
                loadEditor(ConfigKeys.GLOBAL, true);
            } else if (basicDraft != null) {
                loadProfileIntoEditor(basicDraft.profile, false);
                editorLoadedFor = ConfigKeys.GLOBAL;
            } else {
                loadEditor(ConfigKeys.GLOBAL, false);
            }
        } else {
            Profile.AppPolicy policy = Profile.appPolicy(prefs, selectedTarget);
            ui.setChoice(policyChoices, policyIndex(policy));
            profileBody.setVisibility(policy == Profile.AppPolicy.CUSTOM
                    ? View.VISIBLE : View.GONE);
            if (policy == Profile.AppPolicy.CUSTOM) {
                loadEditor(selectedTarget, Profile.hasStoredProfile(prefs, selectedTarget));
            }
        }
        loading = previousLoading;
        updateActionButtons();
        validateAndPreview();
        markEditorClean();
    }

    private void onPolicyChanged() {
        if (isGlobal()) return;
        Profile.AppPolicy policy = selectedPolicy();
        profileBody.setVisibility(policy == Profile.AppPolicy.CUSTOM
                ? View.VISIBLE : View.GONE);
        if (policy == Profile.AppPolicy.CUSTOM && !selectedTarget.equals(editorLoadedFor)) {
            if (Profile.hasStoredProfile(prefs, selectedTarget)) {
                loadEditor(selectedTarget, true);
            } else if (Profile.hasStoredProfile(prefs, ConfigKeys.GLOBAL)) {
                loadProfileIntoEditor(Profile.load(prefs, ConfigKeys.GLOBAL), false);
                editorLoadedFor = selectedTarget;
            } else if (basicDraft != null) {
                loadProfileIntoEditor(basicDraft.profile, false);
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
        boolean previousLoading = loading;
        loading = true;
        if (profile == null) {
            enabled.setChecked(true);
            randomize.setChecked(false);
            hideVpn.setChecked(true);
            hideProxy.setChecked(true);
            hideIpv6.setChecked(false);
            currentSeed = nonZeroRandom();
            identityEditors.clear();
            identitiesContainer.removeAllViews();
            addIdentityEditor(null);
            String country = countryPresetPanel == null
                    ? selectedBasicCountry() : countryPresetPanel.selectedCountryCode();
            dns.setText(DnsPresetProvider.format(country));
        } else {
            enabled.setChecked(profile.enabled);
            randomize.setChecked(profile.randomize);
            hideVpn.setChecked(profile.hideVpn);
            hideProxy.setChecked(profile.hideProxy);
            hideIpv6.setChecked(profile.hideIpv6);
            currentSeed = exists ? profile.selectionSeed : profile.selectionSeed;
            if (currentSeed == 0L) currentSeed = nonZeroRandom();
            identityEditors.clear();
            identitiesContainer.removeAllViews();
            if (profile.identities.isEmpty()) {
                addIdentityEditor(null);
            } else {
                for (NetworkIdentity identity : profile.identities) addIdentityEditor(identity);
            }
            dns.setText(DnsPresetProvider.formatSets(profile.dnsSets));
        }
        loading = previousLoading;
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
                        .putString(ConfigKeys.p(selectedTarget, ConfigKeys.FIELD_POLICY),
                                policy.storedValue)
                        .commit();
                if (!ok) {
                    toast("Save failed");
                    return;
                }
                refreshTargets();
                updateActionButtons();
                updatePreview();
                markEditorClean();
                toast("Saved. Restart the target app to apply.");
                return;
            }
        }

        CollectedProfile collected = collectProfile(true);
        if (collected == null) return;
        String target = isGlobal() ? ConfigKeys.GLOBAL : selectedTarget;
        Profile draft = Profile.create(
                enabled.isChecked(), randomize.isChecked(), hideVpn.isChecked(),
                hideProxy.isChecked(), hideIpv6.isChecked(), currentSeed,
                collected.identities, collected.dnsSets);
        SharedPreferences.Editor editor = prefs.edit();
        ProfilePersistence.putProfile(editor, target, draft);

        if (isGlobal()) {
            if (basicDraft != null && sameProfile(draft, basicDraft.profile)) {
                BasicProfileMetadata.markBasic(editor, basicDraft);
            } else {
                BasicProfileMetadata.clear(editor);
            }
        } else {
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
        markEditorClean();
        rebuildBasicDraft();
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
                requestSelectTarget(entry.target);
                return false;
            }
        }
        if (isPackageName(raw)) {
            requestSelectTarget(raw);
            return false;
        }
        targetField.setError(
                "Choose a listed profile or enter a valid package such as org.example.app");
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
            setValidation(UiFactory.Tone.ERROR, "Add at least one valid network identity.");
            if (focusInvalid && !identityEditors.isEmpty()) identityEditors.get(0).focusIpv4();
            return null;
        }
        if (firstInvalid != null) {
            setValidation(UiFactory.Tone.ERROR,
                    "Fix the highlighted network identity before saving.");
            if (focusInvalid) firstInvalid.focusFirstInvalid();
            return null;
        }
        if (!dnsValidation.valid
                && (requiresData || !dns.getText().toString().trim().isEmpty())) {
            setValidation(UiFactory.Tone.ERROR, dnsValidation.error);
            if (focusInvalid) {
                dns.requestFocus();
                scrollTo(dns);
            }
            return null;
        }
        if (requiresData && dnsValidation.sets.isEmpty()) {
            setValidation(UiFactory.Tone.ERROR, "Add at least one valid DNS set.");
            if (focusInvalid) {
                dns.requestFocus();
                scrollTo(dns);
            }
            return null;
        }

        if (requiresData) setValidation(UiFactory.Tone.SUCCESS, "Configuration is coherent.");
        else setValidation(UiFactory.Tone.INFO,
                "Profile is disabled; incomplete values may be saved.");
        return new CollectedProfile(identities, dnsValidation.sets);
    }

    private DnsValidation validateDns() {
        String raw = dns.getText().toString().trim();
        if (raw.isEmpty()) {
            ui.setStatus(dnsStatus,
                    enabled.isChecked() ? UiFactory.Tone.WARNING : UiFactory.Tone.NEUTRAL,
                    enabled.isChecked()
                            ? "Add at least one DNS set."
                            : "DNS is optional while this profile is disabled.");
            return new DnsValidation(true, null, List.of());
        }

        int lineNumber = 0;
        for (String line : raw.split("[\\r\\n]+")) {
            lineNumber++;
            if (line.trim().isEmpty()) continue;
            for (String token : line.split(",")) {
                String value = token.trim();
                if (value.isEmpty() || !Ipv4.isLiteral(value)) {
                    String error = "DNS set " + lineNumber
                            + " contains invalid IPv4 value: " + value;
                    ui.setStatus(dnsStatus, UiFactory.Tone.ERROR, error);
                    return new DnsValidation(false, error, List.of());
                }
            }
        }

        List<List<String>> sets = Profile.parseDnsSets(raw);
        ui.setStatus(dnsStatus, UiFactory.Tone.SUCCESS,
                "Parsed " + sets.size() + " DNS set" + (sets.size() == 1 ? "" : "s") + ".");
        return new DnsValidation(true, null, sets);
    }

    private void setValidation(UiFactory.Tone tone, String text) {
        ui.setStatus(validationSummary, tone, text);
    }

    private void rerollSelected() {
        if (!isGlobal() && selectedPolicy() != Profile.AppPolicy.CUSTOM) {
            toast("Select Global or a Custom profile to reroll.");
            return;
        }
        String target = isGlobal() ? ConfigKeys.GLOBAL : selectedTarget;
        if (!Profile.hasStoredProfile(prefs, target)) {
            toast("Save this profile first");
            return;
        }
        currentSeed = nonZeroRandom();
        if (!prefs.edit()
                .putLong(ConfigKeys.p(target, ConfigKeys.FIELD_SELECTION_SEED), currentSeed)
                .commit()) {
            toast("Reroll failed");
            return;
        }
        updatePreview();
        markEditorClean();
        rebuildBasicDraft();
        toast(isGlobal()
                ? "Global rerolled. Restart inheriting app processes."
                : "Rerolled. Restart the target app process(es).");
    }

    private void requestClearSelected() {
        runAfterDiscardIfNeeded(this::confirmClearSelected);
    }

    private void confirmClearSelected() {
        new AlertDialog.Builder(this)
                .setTitle("Clear " + selectedProfileName() + "?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> clearSelected())
                .show();
    }

    private void clearSelected() {
        String target = isGlobal() ? ConfigKeys.GLOBAL : selectedTarget;
        SharedPreferences.Editor editor = prefs.edit();
        ProfilePersistence.clearProfile(editor, target);
        if (isGlobal()) {
            BasicProfileMetadata.clear(editor);
        } else {
            editor.remove(ConfigKeys.p(selectedTarget, ConfigKeys.FIELD_POLICY));
            Set<String> index = mutableIndex();
            index.remove(selectedTarget);
            editor.putStringSet(ConfigKeys.INDEX, index);
        }
        if (!editor.commit()) {
            toast("Clear failed");
            return;
        }
        refreshTargets();
        loadSelectedTarget();
        rebuildBasicDraft();
        toast(isGlobal() ? "Global cleared" : "Profile cleared; app now uses Global");
    }

    private String selectedProfileName() {
        if (isGlobal()) return "Global";
        TargetEntry entry = findTarget(selectedTarget);
        return entry == null ? selectedTarget : entry.label;
    }

    private void runAfterDiscardIfNeeded(Runnable action) {
        if (!hasUnsavedChanges()) {
            action.run();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Discard unsaved changes?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Discard", (dialog, which) -> action.run())
                .show();
    }

    private boolean hasUnsavedChanges() {
        return !loading && editorBaseline != null && !editorBaseline.equals(editorSignature());
    }

    private void markEditorClean() {
        editorBaseline = editorSignature();
    }

    private String editorSignature() {
        if (targetField == null || policyChoices == null) return "";
        StringBuilder value = new StringBuilder(selectedTarget);
        Profile.AppPolicy policy = isGlobal() ? Profile.AppPolicy.CUSTOM : selectedPolicy();
        value.append('|').append(policy.storedValue);
        if (!isGlobal() && policy != Profile.AppPolicy.CUSTOM) return value.toString();
        if (enabled == null || dns == null) return value.toString();
        value.append('|').append(enabled.isChecked())
                .append('|').append(randomize.isChecked())
                .append('|').append(hideVpn.isChecked())
                .append('|').append(hideProxy.isChecked())
                .append('|').append(hideIpv6.isChecked())
                .append('|').append(currentSeed)
                .append('|').append(dns.getText().toString());
        for (IdentityEditor editor : identityEditors) {
            value.append("|id=")
                    .append(editor.ip.getText()).append(',')
                    .append(ui.choiceIndex(editor.routeMode)).append(',')
                    .append(editor.prefix.getText()).append(',')
                    .append(editor.gateway.getText());
        }
        return value.toString();
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
                setSummary(UiFactory.Tone.INFO, "OFF FOR THIS APP",
                        appNameLine()
                                + "\n\nNetVeil does not apply profile hooks to this app. "
                                + "Vector / LSPosed may still scope the process.");
                return;
            }
            if (policy == Profile.AppPolicy.INHERIT_GLOBAL) {
                Profile.Resolved resolved = Profile.resolveEffective(prefs, selectedTarget);
                setSummary(resolved == null ? UiFactory.Tone.WARNING : UiFactory.Tone.INFO,
                        "USE GLOBAL",
                        appNameLine() + "\n\n" + resolvedText(resolved)
                                + "\n\nApplies only while this app is scoped in Vector / LSPosed.");
                return;
            }
        }

        CollectedProfile collected = collectProfile(false);
        if (collected == null) {
            setSummary(UiFactory.Tone.WARNING, "NEEDS ATTENTION",
                    (isGlobal() ? "Global profile" : appNameLine())
                            + "\n\nFix the highlighted fields to produce a resolvable profile.");
            return;
        }

        Profile draft = Profile.create(
                enabled.isChecked(), randomize.isChecked(), hideVpn.isChecked(),
                hideProxy.isChecked(), hideIpv6.isChecked(), currentSeed,
                collected.identities, collected.dnsSets);

        if (!enabled.isChecked()) {
            setSummary(UiFactory.Tone.NEUTRAL,
                    isGlobal() ? "GLOBAL · DISABLED" : "CUSTOM · DISABLED",
                    (isGlobal() ? "Global profile" : appNameLine())
                            + "\n\nMasking is disabled for this profile.");
            return;
        }

        if (isGlobal() && randomize.isChecked()) {
            setSummary(UiFactory.Tone.SUCCESS, "GLOBAL · ENABLED",
                    collected.identities.size() + " network identit"
                            + (collected.identities.size() == 1 ? "y" : "ies")
                            + "\n" + collected.dnsSets.size() + " DNS set"
                            + (collected.dnsSets.size() == 1 ? "" : "s")
                            + "\nPer-app deterministic selection\n" + privacyText());
            return;
        }

        Profile.Resolved resolved = draft.resolve();
        setSummary(UiFactory.Tone.SUCCESS,
                isGlobal() ? "GLOBAL · ENABLED" : "CUSTOM · ENABLED",
                (isGlobal() ? "Global profile" : appNameLine())
                        + "\n\n" + resolvedText(resolved)
                        + "\n" + privacyText()
                        + "\n\nApplies only to Vector / LSPosed-scoped processes.");
    }

    private void setSummary(UiFactory.Tone tone, String chip, String body) {
        ui.setChip(summaryChip, tone, chip);
        preview.setText(body);
    }

    private String resolvedText(Profile.Resolved resolved) {
        if (resolved == null) return "Effective profile is disabled or incomplete.";
        String route = resolved.hasExplicitRoute()
                ? "IPv4: " + resolved.ipv4 + "/" + resolved.prefixLength
                        + "\nGateway: " + resolved.gateway
                        + "\nRoutes: explicit virtual network"
                : "IPv4: " + resolved.ipv4 + "\nGateway & routes: omitted";
        return route + "\nDNS: " + String.join(", ", resolved.dns);
    }

    private String privacyText() {
        return "VPN indicators: "
                + (hideVpn.isChecked() ? "hidden from apps" : "preserved")
                + "\nProxy indicators: "
                + (hideProxy.isChecked() ? "hidden from apps" : "preserved")
                + "\nIPv6 addresses: "
                + (hideIpv6.isChecked() ? "suppressed on covered APIs" : "preserved");
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
        reroll.setAlpha(reroll.isEnabled() ? 1f : 0.45f);
        reroll.setText(isGlobal() ? "Reroll Global" : "Reroll");

        boolean canClear = isGlobal()
                ? Profile.hasStoredProfile(prefs, ConfigKeys.GLOBAL)
                : Profile.hasStoredProfile(prefs, selectedTarget)
                        || prefs.contains(ConfigKeys.p(selectedTarget, ConfigKeys.FIELD_POLICY));
        clearProfile.setEnabled(canClear);
        clearProfile.setAlpha(canClear ? 1f : 0.45f);
    }

    private void addIdentityEditor(NetworkIdentity identity) {
        IdentityEditor editor = new IdentityEditor(identity);
        identityEditors.add(editor);
        editor.container.setLayoutParams(ui.blockParams(12));
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
        return switch (ui.choiceIndex(policyChoices)) {
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
        return new LinkedHashSet<>(
                prefs.getStringSet(ConfigKeys.INDEX, new LinkedHashSet<>()));
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

    private void watch(EditText field) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {}

            @Override public void onTextChanged(
                    CharSequence s, int start, int before, int count) {}

            @Override public void afterTextChanged(Editable s) {
                validateAndPreview();
            }
        });
    }

    private void scrollTo(View view) {
        scroll.post(() -> {
            Rect rect = new Rect();
            view.getDrawingRect(rect);
            scroll.offsetDescendantRectToMyCoords(view, rect);
            scroll.smoothScrollTo(0, Math.max(0, rect.top - ui.dp(24)));
        });
    }

    private long nonZeroRandom() {
        long value;
        do value = random.nextLong(); while (value == 0L);
        return value;
    }

    private static boolean sameProfile(Profile left, Profile right) {
        return left != null && right != null
                && left.enabled == right.enabled
                && left.randomize == right.randomize
                && left.hideVpn == right.hideVpn
                && left.hideProxy == right.hideProxy
                && left.hideIpv6 == right.hideIpv6
                && left.selectionSeed == right.selectionSeed
                && NetworkIdentity.serializeList(left.identities)
                        .equals(NetworkIdentity.serializeList(right.identities))
                && DnsPresetProvider.formatSets(left.dnsSets)
                        .equals(DnsPresetProvider.formatSets(right.dnsSets));
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    private final class IdentityEditor {
        final LinearLayout container = ui.innerCard();
        final TextView heading;
        final EditText ip;
        final RadioGroup routeMode;
        final LinearLayout explicitFields = ui.vertical();
        final EditText prefix;
        final EditText gateway;
        final TextView status;

        IdentityEditor(NetworkIdentity identity) {
            heading = ui.subheading("Identity");
            container.addView(heading);
            container.addView(ui.label("IPv4 address"));
            container.addView(ui.helper("The IPv4 value exposed to the target app."));

            ip = ui.input(false);
            ip.setInputType(InputType.TYPE_CLASS_PHONE);
            ip.setHint("202.128.115.2");
            container.addView(ip);

            container.addView(ui.label("Gateway & routes"));
            container.addView(ui.helper(
                    "Omit: expose the configured IPv4 without synthetic gateway or route details. "
                            + "Explicit: provide a coherent prefix and gateway for a virtual LAN."));

            routeMode = ui.choiceGroup(ROUTE_LABELS, false);
            container.addView(routeMode);

            explicitFields.setLayoutParams(ui.matchWrap());
            explicitFields.addView(ui.label("IPv4 prefix length"));
            explicitFields.addView(ui.helper(
                    "0–32. /24 is common; /0 covers the entire IPv4 address space."));

            prefix = ui.input(false);
            prefix.setInputType(InputType.TYPE_CLASS_NUMBER);
            prefix.setText("24");
            explicitFields.addView(prefix);

            explicitFields.addView(ui.label("Gateway"));
            explicitFields.addView(ui.helper(
                    "Must differ from the client IPv4 and be inside the configured subnet."));

            gateway = ui.input(false);
            gateway.setInputType(InputType.TYPE_CLASS_PHONE);
            gateway.setHint("192.168.1.1");
            explicitFields.addView(gateway);
            container.addView(explicitFields);

            status = ui.status("", UiFactory.Tone.NEUTRAL);
            LinearLayout.LayoutParams statusParams = ui.blockParams(8);
            statusParams.topMargin = ui.dp(10);
            status.setLayoutParams(statusParams);
            container.addView(status);

            Button remove = ui.button("Remove", UiFactory.ButtonKind.ERROR);
            remove.setLayoutParams(ui.matchWrap());
            remove.setOnClickListener(v -> removeIdentityEditor(this));
            container.addView(remove);

            if (identity != null) {
                ip.setText(identity.ipv4);
                if (identity.routeMode == NetworkIdentity.RouteMode.EXPLICIT) {
                    ui.setChoice(routeMode, 1);
                    prefix.setText(String.valueOf(identity.prefixLength));
                    gateway.setText(identity.gateway);
                } else {
                    ui.setChoice(routeMode, 0);
                }
            } else {
                ui.setChoice(routeMode, 0);
            }

            explicitFields.setVisibility(
                    ui.choiceIndex(routeMode) == 1 ? View.VISIBLE : View.GONE);

            routeMode.setOnCheckedChangeListener((group, checkedId) -> {
                explicitFields.setVisibility(
                        ui.choiceIndex(routeMode) == 1 ? View.VISIBLE : View.GONE);
                if (!loading) validateAndPreview();
            });

            watch(ip);
            watch(prefix);
            watch(gateway);
            validateNow();
        }

        boolean isBlank() {
            return ip.getText().toString().trim().isEmpty()
                    && (ui.choiceIndex(routeMode) == 0
                    || gateway.getText().toString().trim().isEmpty());
        }

        NetworkIdentity.Validation validateNow() {
            NetworkIdentity.RouteMode mode = ui.choiceIndex(routeMode) == 1
                    ? NetworkIdentity.RouteMode.EXPLICIT : NetworkIdentity.RouteMode.HIDDEN;
            NetworkIdentity.Validation validation = NetworkIdentity.validate(
                    ip.getText().toString(), mode,
                    prefix.getText().toString(), gateway.getText().toString());

            if (isBlank()) {
                ui.setStatus(status, UiFactory.Tone.NEUTRAL,
                        "Enter an IPv4 address, or remove this identity.");
            } else if (!validation.valid) {
                ui.setStatus(status, UiFactory.Tone.ERROR, validation.error);
            } else if (validation.warning != null) {
                ui.setStatus(status, UiFactory.Tone.WARNING, validation.warning);
            } else {
                ui.setStatus(status, UiFactory.Tone.SUCCESS, "Valid identity.");
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
            } else if (ui.choiceIndex(routeMode) == 1) {
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

        @Override
        public String toString() {
            if (ConfigKeys.GLOBAL.equals(target)) return GLOBAL_LABEL;
            return group + " · " + label + " — " + target;
        }
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
