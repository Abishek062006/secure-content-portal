package com.secureportal.ai;

/** One chat completion: a system prompt and a user prompt in, the model's text out. */
public interface LlmClient {

    String complete(String systemPrompt, String userPrompt);
}
