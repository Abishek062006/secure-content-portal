package com.secureportal.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/** Speech-to-text through an OpenAI-compatible /audio/transcriptions endpoint (Groq's Whisper by default). */
@Component
public class OpenAiSpeechClient implements SpeechToText {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public OpenAiSpeechClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String transcribe(byte[] audio, String filename, String contentType) {
        if (!properties.isTranscriptionConfigured()) {
            throw new AiNotConfiguredException();
        }
        String boundary = "----gradientnova" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = multipart(boundary, audio, filename, contentType);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint()))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new AiException("The speech service answered HTTP " + response.statusCode() + ".");
            }
            return objectMapper.readTree(response.body()).path("text").asText("").strip();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("The transcription was interrupted.", e);
        } catch (JsonProcessingException e) {
            throw new AiException("The speech service returned an unreadable response.", e);
        } catch (IOException | IllegalArgumentException e) {
            throw new AiException("Could not reach the speech service: " + e.getMessage(), e);
        }
    }

    private byte[] multipart(String boundary, byte[] audio, String filename, String contentType) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(audio.length + 512)) {
            field(out, boundary, "model", properties.getTranscriptionModel());
            field(out, boundary, "response_format", "json");
            field(out, boundary, "temperature", "0");
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                    + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(audio);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new AiException("Could not build the transcription request.", e);
        }
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private String endpoint() {
        String base = properties.getBaseUrl().trim();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/audio/transcriptions";
    }
}
