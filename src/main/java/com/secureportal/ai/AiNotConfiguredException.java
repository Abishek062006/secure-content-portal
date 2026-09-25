package com.secureportal.ai;

public class AiNotConfiguredException extends RuntimeException {

    public AiNotConfiguredException() {
        super("AI isn't configured. Set AI_MODEL (and AI_BASE_URL / AI_API_KEY for your provider) in .env.local.");
    }
}
