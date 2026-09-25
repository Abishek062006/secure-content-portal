package com.secureportal.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private int rateLimitedResponses = 0;
    private int status = 200;
    private String responseBody = "{\"choices\":[{\"message\":{\"content\":\"hello\"}}]}";

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            boolean limited = calls.incrementAndGet() <= rateLimitedResponses;
            byte[] out = (limited ? "{\"error\":\"rate limited\"}" : responseBody).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            if (limited) {
                exchange.getResponseHeaders().add("Retry-After", "0");
            }
            exchange.sendResponseHeaders(limited ? 429 : status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private OpenAiCompatibleClient client(String apiKey, String model, boolean jsonMode) {
        AiProperties properties = new AiProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
        properties.setApiKey(apiKey);
        properties.setModel(model);
        properties.setJsonMode(jsonMode);
        properties.setTimeoutSeconds(5);
        return new OpenAiCompatibleClient(properties, objectMapper);
    }

    @Test
    void sendsAnOpenAiStyleChatRequestAndReturnsTheReply() throws Exception {
        String reply = client("secret-key", "some-model", true).complete("be brief", "say hello");

        assertThat(reply).isEqualTo("hello");
        assertThat(path.get()).isEqualTo("/v1/chat/completions");
        assertThat(authorization.get()).isEqualTo("Bearer secret-key");
        JsonNode body = objectMapper.readTree(requestBody.get());
        assertThat(body.get("model").asText()).isEqualTo("some-model");
        assertThat(body.get("response_format").get("type").asText()).isEqualTo("json_object");
        assertThat(body.get("messages").get(0).get("role").asText()).isEqualTo("system");
        assertThat(body.get("messages").get(1).get("content").asText()).isEqualTo("say hello");
    }

    @Test
    void omitsTheAuthorizationHeaderAndJsonModeWhenNotConfigured() throws Exception {
        client("", "local-model", false).complete("s", "u");

        assertThat(authorization.get()).isNull();
        assertThat(objectMapper.readTree(requestBody.get()).has("response_format")).isFalse();
    }

    @Test
    void refusesToCallWhenNoModelIsConfigured() {
        assertThatThrownBy(() -> client("k", "", true).complete("s", "u"))
                .isInstanceOf(AiNotConfiguredException.class);
    }

    @Test
    void turnsAnErrorStatusIntoAnAiException() {
        status = 500;
        responseBody = "{\"error\":\"boom\"}";

        assertThatThrownBy(() -> client("k", "m", true).complete("s", "u"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("500");
        assertThat(calls.get()).as("a server error is not retried").isEqualTo(1);
    }

    @Test
    void retriesARateLimitAndThenSucceeds() {
        rateLimitedResponses = 2;

        assertThat(client("k", "m", true).complete("s", "u")).isEqualTo("hello");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void givesUpWhenTheRateLimitDoesNotClear() {
        rateLimitedResponses = 10;

        assertThatThrownBy(() -> client("k", "m", true).complete("s", "u"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("429");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void rejectsAResponseWithNoContent() {
        responseBody = "{\"choices\":[]}";

        assertThatThrownBy(() -> client("k", "m", true).complete("s", "u"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("no content");
    }
}
