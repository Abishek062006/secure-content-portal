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

    /** Asks the provider for strict JSON output; turn off for a provider that rejects the parameter. */
    private boolean jsonMode = true;

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank() && model != null && !model.isBlank();
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
