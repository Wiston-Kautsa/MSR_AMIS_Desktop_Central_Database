package com.mycompany.msr.amis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        String body = response.body() == null ? "" : response.body();

        if (status >= 200 && status < 300) {
            if (responseType == Void.class || body.isBlank()) {
                return null;
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
                if (jsonNode.hasNonNull("message")) {
                    message = jsonNode.get("message").asText();
                }
            } catch (Exception ignored) {
                message = body;
            }
        }

        if (status == 401 || status == 403) {
            return new ApiClientException(status, message);
        }
        return new ApiClientException(status, message);
    }
}
