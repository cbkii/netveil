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

/** Bundled -> cached -> refreshed country-data store. Refresh never destroys the last valid pack. */
public final class CountryPackStore {
    public static final String ASSET_NAME = "country-ip-pack.json";
    public static final String CACHE_NAME = "country-ip-pack.json";
    public static final int MAX_BYTES = 256 * 1024;
    public static final String UPDATE_URL =
            "https://raw.githubusercontent.com/cbkii/netveil/main/app/src/main/assets/country-ip-pack.json";

    private CountryPackStore() {}

    public static Loaded loadBest(Context context) throws IOException, JSONException {
        Objects.requireNonNull(context, "context");
        CountryPack bundled = CountryPack.parse(readAsset(context));
        Path cache = context.getFilesDir().toPath().resolve(CACHE_NAME);
        if (!Files.isRegularFile(cache)) return new Loaded(bundled, "bundled");
        try {
            CountryPack cached = CountryPack.parse(readUtf8(cache));
            return cached.isAtLeastAsNewAs(bundled)
                    ? new Loaded(cached, "cached") : new Loaded(bundled, "bundled");
        } catch (IOException | JSONException ignored) {
            return new Loaded(bundled, "bundled");
        }
    }

    public static RefreshResult refreshBlocking(Context context) {
        Path cache = context.getFilesDir().toPath().resolve(CACHE_NAME);
        Path temp = context.getFilesDir().toPath().resolve(CACHE_NAME + ".tmp");
        try {
            CountryPack current = loadBest(context).pack;
            byte[] bytes = fetchHttps(UPDATE_URL);
            String text = new String(bytes, StandardCharsets.UTF_8);
            CountryPack parsed = CountryPack.parse(text);
            if (!parsed.isAtLeastAsNewAs(current)) {
                throw new IOException("downloaded country pack is older than the current valid data");
            }

            Files.write(temp, bytes);
            try {
                Files.move(temp, cache, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Never weaken the cache contract to a non-atomic replacement. The bundled/old
                // cache remains authoritative and the temporary file is removed by the outer catch.
                throw new IOException("atomic country-pack cache replacement is unavailable", e);
            }
            return RefreshResult.success(parsed.generatedAt);
        } catch (Exception e) {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            return RefreshResult.failure(e.getClass().getSimpleName() + ": "
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
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "NetVeil-country-pack/1");
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
        public final String source;
        Loaded(CountryPack pack, String source) {
            this.pack = pack;
            this.source = source;
        }
    }

    public static final class RefreshResult {
        public final boolean success;
        public final String generatedAt;
        public final String error;
        private RefreshResult(boolean success, String generatedAt, String error) {
            this.success = success;
            this.generatedAt = generatedAt;
            this.error = error;
        }
        static RefreshResult success(String generatedAt) {
            return new RefreshResult(true, generatedAt, null);
        }
        static RefreshResult failure(String error) {
            return new RefreshResult(false, null, error);
        }
    }
}
