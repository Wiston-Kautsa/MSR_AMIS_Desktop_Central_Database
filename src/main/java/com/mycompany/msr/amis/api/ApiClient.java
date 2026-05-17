package com.mycompany.msr.amis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

public final class ApiClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final String baseUrl;
    private final HttpClient httpClient;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public <T> T get(String path, Class<T> responseType) throws Exception {
        return send(buildRequest(path).GET().build(), responseType);
    }

    public JsonNode getJson(String path) throws Exception {
        return send(buildRequest(path).GET().build(), JsonNode.class);
    }

    public <T> T post(String path, Object requestBody, Class<T> responseType) throws Exception {
        return send(writeJson(buildRequest(path), requestBody).POST(HttpRequest.BodyPublishers.ofString(serialize(requestBody))).build(), responseType);
    }

    public <T> T put(String path, Object requestBody, Class<T> responseType) throws Exception {
        return send(writeJson(buildRequest(path), requestBody).PUT(HttpRequest.BodyPublishers.ofString(serialize(requestBody))).build(), responseType);
    }

    public <T> T patch(String path, Object requestBody, Class<T> responseType) throws Exception {
        return send(writeJson(buildRequest(path), requestBody).method("PATCH", HttpRequest.BodyPublishers.ofString(serialize(requestBody))).build(), responseType);
    }

    public JsonNode delete(String path) throws Exception {
        return send(buildRequest(path).DELETE().build(), JsonNode.class);
    }

    private HttpRequest.Builder buildRequest(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json");

        String authToken = Session.getAuthToken();
        if (authToken != null && !authToken.isBlank()) {
            builder.header("Authorization", "Bearer " + authToken);
        }
        return builder;
    }

    private HttpRequest.Builder writeJson(HttpRequest.Builder builder, Object requestBody) {
        return builder.header("Content-Type", "application/json");
    }

    private String serialize(Object requestBody) throws IOException {
        return OBJECT_MAPPER.writeValueAsString(requestBody);
    }

    private <T> T send(HttpRequest request, Class<T> responseType) throws Exception {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (ConnectException | HttpTimeoutException exception) {
            throw new ApiClientException(0, "API is not reachable at " + baseUrl + ". Start the API server and try again.");
        }
        int status = response.statusCode();
        String body = response.body() == null ? "" : response.body();

        if (status >= 200 && status < 300) {
            if (responseType == Void.class || body.isBlank()) {
                return null;
            }
            if (responseType == String.class) {
                return responseType.cast(body);
            }
            return OBJECT_MAPPER.readValue(body, responseType);
        }

        throw toException(status, body);
    }

    private ApiClientException toException(int status, String body) {
        String message = "Request failed.";
        if (body != null && !body.isBlank()) {
            try {
                JsonNode jsonNode = OBJECT_MAPPER.readTree(body);
                message = extractErrorMessage(jsonNode, message);
            } catch (Exception ignored) {
                message = body;
            }
        }

        if (status == 401 || status == 403) {
            return new ApiClientException(status, message);
        }
        return new ApiClientException(status, message);
    }

    private String extractErrorMessage(JsonNode jsonNode, String fallback) {
        for (String field : new String[]{"message", "error", "detail", "title"}) {
            if (jsonNode.hasNonNull(field) && !jsonNode.get(field).asText().isBlank()) {
                return jsonNode.get(field).asText();
            }
        }
        if (jsonNode.hasNonNull("status")) {
            return "Request failed with status " + jsonNode.get("status").asText() + ".";
        }
        return fallback;
    }
}
