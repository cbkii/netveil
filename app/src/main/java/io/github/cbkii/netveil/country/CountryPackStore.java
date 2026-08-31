package io.github.cbkii.netveil.country;

import android.content.Context;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

import javax.net.ssl.HttpsURLConnection;

/** Bundled -> validated online cache country-data store. Refresh never destroys the last valid pack. */
public final class CountryPackStore {
    public static final String ASSET_NAME = "country-ip-pack.json";
    public static final String CACHE_NAME = "country-ip-pack.json";
    public static final int MAX_BYTES = 256 * 1024;
    /** Canonical public pack in the same public repository that builds the bundled APK asset. */
    public static final String UPDATE_URL =
            "https://raw.githubusercontent.com/cbkii/netveil/main/app/src/main/assets/country-ip-pack.json";

    public enum Source {
        BUNDLED("Bundled with APK"),
        ONLINE_CACHE("Online cache");

        public final String label;

        Source(String label) {
            this.label = label;
        }
    }

    public enum Outcome {
        UPDATED,
        UNCHANGED,
        FAILED
    }

    private CountryPackStore() {}

    public static Loaded loadBest(Context context) throws IOException, JSONException {
        Objects.requireNonNull(context, "context");
        CountryPack bundled = CountryPack.parse(readAsset(context));
        Path cache = context.getFilesDir().toPath().resolve(CACHE_NAME);
        if (!Files.isRegularFile(cache)) return new Loaded(bundled, Source.BUNDLED);
        try {
            CountryPack cached = CountryPack.parse(readUtf8(cache));
            return cached.isAtLeastAsNewAs(bundled)
                    ? new Loaded(cached, Source.ONLINE_CACHE)
                    : new Loaded(bundled, Source.BUNDLED);
        } catch (IOException | JSONException ignored) {
            return new Loaded(bundled, Source.BUNDLED);
        }
    }

    public static RefreshResult refreshBlocking(Context context) {
        Path cache = context.getFilesDir().toPath().resolve(CACHE_NAME);
        Path temp = context.getFilesDir().toPath().resolve(CACHE_NAME + ".tmp");
        Loaded currentLoaded = null;
        long checkedAtMillis = 0L;
        try {
            currentLoaded = loadBest(context);
            checkedAtMillis = System.currentTimeMillis();
            byte[] bytes = fetchHttps(UPDATE_URL);
            String text = new String(bytes, StandardCharsets.UTF_8);
            CountryPack remote = CountryPack.parse(text);
            CountryPack.UpdateDisposition disposition =
                    CountryPack.classifyUpdate(currentLoaded.pack, remote);
            switch (disposition) {
                case OLDER -> throw new IOException(
                        "downloaded country pack is older than the current valid data");
                case SAME_VERSION_CONFLICT -> throw new IOException(
                        "downloaded country pack changed without advancing generated_at");
                case UNCHANGED -> {
                    Files.deleteIfExists(temp);
                    return RefreshResult.unchanged(remote.generatedAt, checkedAtMillis,
                            currentLoaded.source);
                }
                case UPDATED -> {
                    Files.write(temp, bytes);
                    try {
                        Files.move(temp, cache, StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException e) {
                        throw new IOException("atomic country-pack cache replacement is unavailable", e);
                    }
                    return RefreshResult.updated(remote.generatedAt, checkedAtMillis);
                }
            }
            throw new IOException("unhandled country-pack update state");
        } catch (Exception e) {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            return RefreshResult.failure(checkedAtMillis, currentLoaded == null ? null : currentLoaded.source,
                    e.getClass().getSimpleName() + ": "
                            + (e.getMessage() == null ? "refresh failed" : e.getMessage()));
        }
    }

    static byte[] fetchHttps(String urlText) throws IOException {
        URL url = new URL(urlText);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("Country pack requires HTTPS");
        }
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(12_000);
        connection.setInstanceFollowRedirects(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("User-Agent", "NetVeil-country-pack/2");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + status);
            }
            if (!"https".equalsIgnoreCase(connection.getURL().getProtocol())) {
                throw new IOException("Country pack redirect left HTTPS");
            }
            int declared = connection.getContentLength();
            if (declared > MAX_BYTES) throw new IOException("country pack is too large");
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_BYTES) throw new IOException("country pack is too large");
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readAsset(Context context) throws IOException {
        try (InputStream input = context.getAssets().open(ASSET_NAME);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) throw new IOException("bundled country pack is too large");
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String readUtf8(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length > MAX_BYTES) throw new IOException("cached country pack is too large");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static final class Loaded {
        public final CountryPack pack;
        public final Source source;

        Loaded(CountryPack pack, Source source) {
            this.pack = pack;
            this.source = source;
        }
    }

    public static final class RefreshResult {
        public final Outcome outcome;
        public final String generatedAt;
        public final long checkedAtMillis;
        public final Source activeSource;
        public final String error;

        private RefreshResult(Outcome outcome, String generatedAt, long checkedAtMillis,
                              Source activeSource, String error) {
            this.outcome = outcome;
            this.generatedAt = generatedAt;
            this.checkedAtMillis = checkedAtMillis;
            this.activeSource = activeSource;
            this.error = error;
        }

        static RefreshResult updated(String generatedAt, long checkedAtMillis) {
            return new RefreshResult(Outcome.UPDATED, generatedAt, checkedAtMillis,
                    Source.ONLINE_CACHE, null);
        }

        static RefreshResult unchanged(String generatedAt, long checkedAtMillis, Source source) {
            return new RefreshResult(Outcome.UNCHANGED, generatedAt, checkedAtMillis, source, null);
        }

        static RefreshResult failure(long checkedAtMillis, Source source, String error) {
            return new RefreshResult(Outcome.FAILED, null, checkedAtMillis, source,
                    error == null || error.isBlank() ? "Refresh failed" : error);
        }
    }
}
