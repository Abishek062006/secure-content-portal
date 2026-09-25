package com.secureportal.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class OpenAiCompatibleClient implements LlmClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long MAX_RETRY_WAIT_SECONDS = 15;

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public OpenAiCompatibleClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (!properties.isConfigured()) {
            throw new AiNotConfiguredException();
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getModel());
        body.put("temperature", 0.3);
        if (properties.isJsonMode()) {
            body.putObject("response_format").put("type", "json_object");
        }
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new AiException("Could not build the AI request.", e);
        }

        try {
            HttpResponse<String> response = null;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint()))
                        .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload));
                if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
                    request.header("Authorization", "Bearer " + properties.getApiKey());
                }
                response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
                if (!retryable(response.statusCode()) || attempt == MAX_ATTEMPTS) {
                    break;
                }
                Thread.sleep(retryDelayMillis(response, attempt));
            }
            if (response.statusCode() / 100 != 2) {
                throw new AiException("The AI service answered HTTP " + response.statusCode() + ": "
                        + abbreviate(response.body()));
            }

            JsonNode content = objectMapper.readTree(response.body()).path("choices").path(0).path("message").path("content");
            if (!content.isTextual()) {
                throw new AiException("The AI service returned no content.");
            }
            return content.asText();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("The AI request was interrupted.", e);
        } catch (JsonProcessingException e) {
            throw new AiException("The AI service returned an unreadable response.", e);
        } catch (IOException | IllegalArgumentException e) {
            throw new AiException("Could not reach the AI service: " + e.getMessage(), e);
        }
    }

    /** Free tiers rate-limit (429) and briefly overload (503); both usually clear within seconds. */
    private static boolean retryable(int status) {
        return status == 429 || status == 503;
    }

    private static long retryDelayMillis(HttpResponse<String> response, int attempt) {
        long seconds = 2L * attempt;
        String header = response.headers().firstValue("Retry-After").orElse(null);
        if (header != null) {
            try {
                seconds = Long.parseLong(header.trim());
            } catch (NumberFormatException e) {
                // Not a number of seconds (could be a date) — keep the default backoff.
            }
        }
        return Math.min(Math.max(seconds, 0), MAX_RETRY_WAIT_SECONDS) * 1000;
    }

    private String endpoint() {
        String base = properties.getBaseUrl().trim();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/chat/completions";
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }
}
