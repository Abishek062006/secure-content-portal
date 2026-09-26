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
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OpenAiCompatibleClient implements LlmClient {

    private static final int MAX_ATTEMPTS = 5;
    private static final long MAX_RETRY_WAIT_SECONDS = 30;
    /** Most calls running at once app-wide, so several admins queue up instead of all hitting the rate limit together. */
    private static final int MAX_CONCURRENT_CALLS = 2;

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final Semaphore slots = new Semaphore(MAX_CONCURRENT_CALLS, true);
    /** What the provider last said we may still spend this minute, and when that resets. */
    private volatile long tokensRemaining = Long.MAX_VALUE;
    private volatile long tokensResetAtMillis;
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
            slots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("The AI request was interrupted.", e);
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
                waitForTokenBudget(payload.length() / 3 + 1500);
                response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
                noteRateLimit(response);
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
        } finally {
            slots.release();
        }
    }

    /** If the provider said the per-minute token budget is nearly spent, wait for it to refill rather than get a 429. */
    private void waitForTokenBudget(long estimatedTokens) throws InterruptedException {
        long wait = tokensResetAtMillis - System.currentTimeMillis();
        if (tokensRemaining < estimatedTokens && wait > 0) {
            Thread.sleep(Math.min(wait + 200, MAX_RETRY_WAIT_SECONDS * 1000));
        }
    }

    private void noteRateLimit(HttpResponse<String> response) {
        response.headers().firstValue("x-ratelimit-remaining-tokens").ifPresent(value -> {
            try {
                tokensRemaining = Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                tokensRemaining = Long.MAX_VALUE;
            }
        });
        response.headers().firstValue("x-ratelimit-reset-tokens")
                .ifPresent(value -> tokensResetAtMillis = System.currentTimeMillis() + parseDurationMillis(value));
    }

    private static final Pattern DURATION_PART = Pattern.compile("(\\d+(?:\\.\\d+)?)(ms|s|m|h)");

    /** Parses the "1m36.4s" / "577ms" style durations rate-limit headers use. */
    static long parseDurationMillis(String value) {
        double millis = 0;
        Matcher matcher = DURATION_PART.matcher(value == null ? "" : value);
        while (matcher.find()) {
            double amount = Double.parseDouble(matcher.group(1));
            millis += switch (matcher.group(2)) {
                case "ms" -> amount;
                case "s" -> amount * 1000;
                case "m" -> amount * 60_000;
                default -> amount * 3_600_000;
            };
        }
        return (long) millis;
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
