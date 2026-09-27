package com.secureportal.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Groq by default, but any OpenAI-compatible chat endpoint works — Gemini,
 * OpenRouter, Mistral, or a local Ollama — so switching provider is only these settings.
 */
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String baseUrl = "https://api.groq.com/openai/v1";

    /** Empty is fine for local providers such as Ollama. */
    private String apiKey = "";

    private String model = "";

    private int timeoutSeconds = 120;

    /** The speech-to-text model used for spoken interview answers, on the same provider and key. */
    private String transcriptionModel = "whisper-large-v3";

    /** The language spoken, as an ISO code (en). Telling Whisper avoids it guessing wrong on short or accented clips. Blank lets it detect. */
    private String transcriptionLanguage = "en";

    /** Asks the provider for strict JSON output; turn off for a provider that rejects the parameter. */
    private boolean jsonMode = true;

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank() && model != null && !model.isBlank();
    }

    /** Speech-to-text needs a key: the local providers that run without one don't offer it. */
    public boolean isTranscriptionConfigured() {
        return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank()
                && transcriptionModel != null && !transcriptionModel.isBlank();
    }

    public String getTranscriptionLanguage() {
        return transcriptionLanguage;
    }

    public void setTranscriptionLanguage(String transcriptionLanguage) {
        this.transcriptionLanguage = transcriptionLanguage;
    }

    public String getTranscriptionModel() {
        return transcriptionModel;
    }

    public void setTranscriptionModel(String transcriptionModel) {
        this.transcriptionModel = transcriptionModel;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isJsonMode() {
        return jsonMode;
    }

    public void setJsonMode(boolean jsonMode) {
        this.jsonMode = jsonMode;
    }
}
