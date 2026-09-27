package com.secureportal.ai;

/** Turns a recording of someone speaking into text. */
public interface SpeechToText {

    /**
     * The words spoken in {@code audio}, or an {@link AiException} if it couldn't be transcribed. {@code hint} (may be null) is a short
     * line of context, such as the role and the terms likely to come up, that helps the model spell technical words correctly.
     */
    String transcribe(byte[] audio, String filename, String contentType, String hint);
}
