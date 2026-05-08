package com.mycompany.msr.amis;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AppConfiguration {

    private static final String DATA_MODE_PROPERTY = "msr.amis.data.mode";
    private static final String DATA_MODE_ENV = "MSR_AMIS_DATA_MODE";
    private static final String DATA_MODE_ENV_ALIAS = "APP_MODE";
    private static final String API_BASE_URL_PROPERTY = "msr.amis.api.base-url";
    private static final String API_BASE_URL_ENV = "MSR_AMIS_API_BASE_URL";
    private static final String API_BASE_URL_ENV_ALIAS = "API_BASE_URL";
    private static final Path ENV_FILE = Path.of(".env");
    private static final Duration API_PROBE_TIMEOUT = Duration.ofSeconds(4);

    private final DataAccessMode configuredMode;
    private final DataAccessMode dataAccessMode;
    private final String apiBaseUrl;

    private AppConfiguration(DataAccessMode configuredMode, DataAccessMode dataAccessMode, String apiBaseUrl) {
        this.configuredMode = configuredMode;
        this.dataAccessMode = dataAccessMode;
        this.apiBaseUrl = apiBaseUrl;
    }

    public static AppConfiguration load() {
        Map<String, String> envFileValues = loadEnvFile();

        String configuredMode = resolveValue(
                DATA_MODE_PROPERTY,
                DATA_MODE_ENV,
                DATA_MODE_ENV_ALIAS,
                envFileValues
        );
        String configuredApiBaseUrl = resolveValue(
                API_BASE_URL_PROPERTY,
                API_BASE_URL_ENV,
                API_BASE_URL_ENV_ALIAS,
                envFileValues
        );
        if (configuredApiBaseUrl == null || configuredApiBaseUrl.isBlank()) {
            configuredApiBaseUrl = "http://localhost:8090";
        }
        DataAccessMode mode = DataAccessMode.from(configuredMode);
        if (mode == DataAccessMode.REMOTE_API && configuredApiBaseUrl.isBlank()) {
            throw new IllegalStateException("REMOTE_API mode requires an API base URL.");
        }
        String normalizedApiBaseUrl = configuredApiBaseUrl.trim();
        DataAccessMode effectiveMode = resolveEffectiveMode(mode, normalizedApiBaseUrl);
        return new AppConfiguration(mode, effectiveMode, normalizedApiBaseUrl);
    }

    private static DataAccessMode resolveEffectiveMode(DataAccessMode configuredMode, String apiBaseUrl) {
        if (configuredMode == DataAccessMode.AUTO) {
            return isApiReachable(apiBaseUrl)
                    ? DataAccessMode.REMOTE_API
                    : DataAccessMode.LOCAL_DATABASE;
        }
        return configuredMode;
    }

    public DataAccessMode getDataAccessMode() {
        return dataAccessMode;
    }

    public DataAccessMode getConfiguredMode() {
        return configuredMode;
    }

    public boolean usesLocalDatabase() {
        return dataAccessMode == DataAccessMode.LOCAL_DATABASE;
    }

    public boolean usesRemoteApi() {
        return dataAccessMode == DataAccessMode.REMOTE_API;
    }

    public boolean isAutomaticMode() {
        return configuredMode == DataAccessMode.AUTO;
    }

    public boolean isUsingLocalFallback() {
        return configuredMode == DataAccessMode.AUTO && dataAccessMode == DataAccessMode.LOCAL_DATABASE;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    private static boolean isApiReachable(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(API_PROBE_TIMEOUT)
                .build();

        return isReachable(client, joinPath(baseUrl, "/actuator/health"))
                || isReachable(client, joinPath(baseUrl, "/"));
    }

    private static boolean isReachable(HttpClient client, String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(API_PROBE_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String joinPath(String baseUrl, String path) {
        String normalizedBase = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        return normalizedBase + path;
    }

    private static String resolveValue(String propertyKey, String envKey, String aliasKey, Map<String, String> envFileValues) {
        String value = System.getProperty(propertyKey);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }

        value = System.getenv(envKey);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }

        value = System.getenv(aliasKey);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }

        value = envFileValues.get(envKey);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }

        value = envFileValues.get(aliasKey);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }

        return null;
    }

    private static Map<String, String> loadEnvFile() {
        Map<String, String> values = new HashMap<>();
        if (!Files.exists(ENV_FILE)) {
            return values;
        }

        try {
            List<String> lines = Files.readAllLines(ENV_FILE);
            for (String rawLine : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int separator = line.indexOf('=');
                if (separator <= 0) {
                    continue;
                }

                String key = line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException ignored) {
            // Keep startup resilient; OS env and system properties still work.
        }
        return values;
    }
}
