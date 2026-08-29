package io.github.cbkii.netveil;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import io.github.cbkii.netveil.config.ConfigKeys;
import io.github.cbkii.netveil.config.Profile;

import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MainActivity extends Activity {
    private final SecureRandom random = new SecureRandom();

    private SharedPreferences prefs;
    private EditText pkg;
    private EditText ipv4;
    private EditText gateway;
    private EditText dns;
    private EditText prefix;
    private CheckBox enabled;
    private CheckBox randomize;
    private CheckBox hideVpn;
    private CheckBox hideProxy;
    private CheckBox hideIpv6;
    private TextView profiles;
    private TextView selection;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(ConfigKeys.PREFS, MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        // Android 15 enforces edge-to-edge for targetSdk 35+. Keep the simple programmatic UI
        // clear of status/navigation bars and display cutouts without adding AndroidX solely for
        // inset handling.
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
                "Per-app API-visible network identity masking for Android 15+. "
                        + "NetVeil does not reroute traffic or alter the public IP seen by remote servers.",
                14));

        pkg = field("Package name (e.g. com.example.app)", false);
        root.addView(pkg);
        enabled = check("Enable profile", true);
        root.addView(enabled);
        randomize = check("Randomise from whitelists (stable across app processes)", false);
        root.addView(randomize);
        hideVpn = check("Hide VPN transport/interface indicators", true);
        root.addView(hideVpn);
        hideProxy = check("Hide HTTP/SOCKS proxy indicators", true);
        root.addView(hideProxy);
        hideIpv6 = check("Suppress IPv6 addresses from covered APIs", true);
        root.addView(hideIpv6);

        prefix = field("IPv4 prefix length (default 24)", false);
        prefix.setInputType(InputType.TYPE_CLASS_NUMBER);
        prefix.setText("24");
        root.addView(prefix);
        ipv4 = field("Allowed IPv4 values — comma/newline separated", true);
        root.addView(ipv4);
        gateway = field("Allowed gateways — comma/newline separated", true);
        root.addView(gateway);
        dns = field("Allowed DNS sets — one set per line; comma-separated within a set", true);
        root.addView(dns);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button load = button("Load");
        Button save = button("Save");
        Button reroll = button("Reroll");
        Button delete = button("Delete");
        buttons.addView(load);
        buttons.addView(save);
        buttons.addView(reroll);
        buttons.addView(delete);
        root.addView(buttons);

        selection = text("", 13);
        root.addView(selection);
        profiles = text("", 13);
        root.addView(profiles);

        load.setOnClickListener(v -> loadProfile());
        save.setOnClickListener(v -> saveProfile());
        reroll.setOnClickListener(v -> rerollProfile());
        delete.setOnClickListener(v -> deleteProfile());

        refreshIndex();
        setContentView(scroll);
        scroll.requestApplyInsets();
    }

    private void loadProfile() {
        String packageName = packageName();
        if (packageName == null) return;

        enabled.setChecked(prefs.getBoolean(ConfigKeys.p(packageName, "enabled"), false));
        randomize.setChecked(prefs.getBoolean(ConfigKeys.p(packageName, "randomize"), false));
        hideVpn.setChecked(prefs.getBoolean(ConfigKeys.p(packageName, "hide_vpn"), true));
        hideProxy.setChecked(prefs.getBoolean(ConfigKeys.p(packageName, "hide_proxy"), true));
        hideIpv6.setChecked(prefs.getBoolean(ConfigKeys.p(packageName, "hide_ipv6"), true));
        prefix.setText(String.valueOf(prefs.getInt(ConfigKeys.p(packageName, "prefix"), 24)));
        ipv4.setText(prefs.getString(ConfigKeys.p(packageName, "ipv4"), ""));
        gateway.setText(prefs.getString(ConfigKeys.p(packageName, "gateways"), ""));
        dns.setText(prefs.getString(ConfigKeys.p(packageName, "dns"), ""));
        showSelection(packageName);
        toast("Loaded " + packageName);
    }

    private void saveProfile() {
        String packageName = packageName();
        if (packageName == null) return;

        Integer parsedPrefix = parsePrefix();
        if (parsedPrefix == null) return;

        List<String> ips = Profile.parseList(ipv4.getText().toString());
        List<String> gateways = Profile.parseList(gateway.getText().toString());
        List<List<String>> dnsSets = Profile.parseDnsSets(dns.getText().toString());

        if (enabled.isChecked()) {
            if (ips.isEmpty()) {
                toast("Add at least one valid IPv4 value");
                return;
            }
            if (gateways.isEmpty()) {
                toast("Add at least one valid gateway");
                return;
            }
            if (dnsSets.isEmpty()) {
                toast("Add at least one valid DNS set");
                return;
            }
            if (!Profile.allIpsHaveCompatibleGateway(ips, gateways, parsedPrefix)) {
                toast("Every allowed IPv4 must have a different gateway in its configured subnet");
                return;
            }
        }

        Set<String> index = new LinkedHashSet<>(
                prefs.getStringSet(ConfigKeys.INDEX, new LinkedHashSet<>()));
        index.add(packageName);

        String seedKey = ConfigKeys.p(packageName, "selection_seed");
        long existingSeed = prefs.getLong(seedKey, Long.MIN_VALUE);
        long seed = existingSeed == Long.MIN_VALUE ? random.nextLong() : existingSeed;

        boolean ok = prefs.edit()
                .putStringSet(ConfigKeys.INDEX, index)
                .putBoolean(ConfigKeys.p(packageName, "enabled"), enabled.isChecked())
                .putBoolean(ConfigKeys.p(packageName, "randomize"), randomize.isChecked())
                .putBoolean(ConfigKeys.p(packageName, "hide_vpn"), hideVpn.isChecked())
                .putBoolean(ConfigKeys.p(packageName, "hide_proxy"), hideProxy.isChecked())
                .putBoolean(ConfigKeys.p(packageName, "hide_ipv6"), hideIpv6.isChecked())
                .putInt(ConfigKeys.p(packageName, "prefix"), parsedPrefix)
                .putLong(seedKey, seed)
                .putString(ConfigKeys.p(packageName, "ipv4"), ipv4.getText().toString().trim())
                .putString(ConfigKeys.p(packageName, "gateways"), gateway.getText().toString().trim())
                .putString(ConfigKeys.p(packageName, "dns"), dns.getText().toString().trim())
                .commit();

        if (!ok) {
            toast("Save failed");
            return;
        }

        refreshIndex();
        showSelection(packageName);
        toast("Saved. Force-stop/restart the target app to apply.");
    }

    private void rerollProfile() {
        String packageName = packageName();
        if (packageName == null) return;
        if (!prefs.contains(ConfigKeys.p(packageName, "enabled"))) {
            toast("Save this profile first");
            return;
        }
        boolean ok = prefs.edit()
                .putLong(ConfigKeys.p(packageName, "selection_seed"), random.nextLong())
                .commit();
        if (!ok) {
            toast("Reroll failed");
            return;
        }
        showSelection(packageName);
        toast("Rerolled. Force-stop/restart every target-app process.");
    }

    private void deleteProfile() {
        String packageName = packageName();
        if (packageName == null) return;

        Set<String> index = new LinkedHashSet<>(
                prefs.getStringSet(ConfigKeys.INDEX, new LinkedHashSet<>()));
        index.remove(packageName);
        SharedPreferences.Editor editor = prefs.edit().putStringSet(ConfigKeys.INDEX, index);
        for (String key : new String[]{
                "enabled", "randomize", "hide_vpn", "hide_proxy", "hide_ipv6",
                "prefix", "selection_seed", "ipv4", "gateways", "dns"}) {
            editor.remove(ConfigKeys.p(packageName, key));
        }
        if (!editor.commit()) {
            toast("Delete failed");
            return;
        }
        selection.setText("");
        refreshIndex();
        toast("Deleted " + packageName);
    }

    private void showSelection(String packageName) {
        Profile.Resolved resolved = Profile.load(prefs, packageName).resolve();
        if (resolved == null) {
            selection.setText("Resolved profile: disabled or incomplete");
            return;
        }
        selection.setText(
                "Resolved profile (stable until Reroll):\n"
                        + "IPv4: " + resolved.ipv4 + "/" + resolved.prefixLength + "\n"
                        + "Gateway: " + resolved.gateway + "\n"
                        + "DNS: " + String.join(", ", resolved.dns));
    }

    private Integer parsePrefix() {
        int value;
        try {
            value = Integer.parseInt(prefix.getText().toString().trim());
        } catch (NumberFormatException e) {
            toast("Prefix must be 0–32");
            return null;
        }
        if (value < 0 || value > 32) {
            toast("Prefix must be 0–32");
            return null;
        }
        return value;
    }

    private void refreshIndex() {
        Set<String> set = prefs.getStringSet(ConfigKeys.INDEX, new LinkedHashSet<>());
        profiles.setText(set.isEmpty()
                ? "Configured profiles: none"
                : "Configured profiles:\n• " + String.join("\n• ", set));
    }

    private String packageName() {
        String value = pkg.getText().toString().trim();
        if (value.isEmpty() || !value.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) {
            toast("Enter a valid package name");
            return null;
        }
        return value;
    }

    private EditText field(String hint, boolean multiLine) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        if (multiLine) {
            editText.setMinLines(2);
            editText.setGravity(android.view.Gravity.TOP);
        }
        editText.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return editText;
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

    private TextView text(String value, float size) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(size);
        textView.setPadding(0, dp(6), 0, dp(6));
        return textView;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }
}
