package com.mycompany.msr.amis;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class ConnectionStatusService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    public ConnectionStatus checkStatus() {
        String baseUrl = ServiceRegistry.getConfiguration().getApiBaseUrl();
        boolean centralReachable = isReachable(joinPath(baseUrl, "/actuator/health")) || isReachable(joinPath(baseUrl, "/"));

        if (ServiceRegistry.getConfiguration().isAutomaticMode()) {
            if (centralReachable) {
                return new ConnectionStatus(
                        "ONLINE (AUTO)",
                        "PostgreSQL is reachable through the API. Local SQLite is kept as the working mirror and offline safety copy.",
                        "connection-status-online"
                );
            }
            return new ConnectionStatus(
                    "OFFLINE (AUTO)",
                    "Central API is unreachable. Working against SQLite and queuing changes for PostgreSQL sync.",
                    "connection-status-offline"
            );
        }

        if (ServiceRegistry.getConfiguration().usesLocalDatabase()) {
            return new ConnectionStatus(
                    "LOCAL DATABASE",
                    "Working against the local desktop database.",
                    "connection-status-local"
            );
        }

        if (centralReachable) {
            return new ConnectionStatus(
                    "ONLINE",
                    "Connected to the central database server.",
                    "connection-status-online"
            );
        }

        return new ConnectionStatus(
                "OFFLINE",
                "Central database server is unreachable.",
                "connection-status-offline"
        );
    }

    private boolean isReachable(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String joinPath(String baseUrl, String path) {
        String normalizedBase = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        return normalizedBase + path;
    }

    public static final class ConnectionStatus {
        private final String label;
        private final String detail;
        private final String styleClass;

        public ConnectionStatus(String label, String detail, String styleClass) {
            this.label = label;
            this.detail = detail;
            this.styleClass = styleClass;
        }

        public String getLabel() {
            return label;
        }

        public String getDetail() {
            return detail;
        }

        public String getStyleClass() {
            return styleClass;
        }
    }
}
