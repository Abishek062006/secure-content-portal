package com.secureportal.ai;

/** Turns a recording of someone speaking into text. */
public interface SpeechToText {

    /** The words spoken in {@code audio}, or an {@link AiException} if it couldn't be transcribed. */
    String transcribe(byte[] audio, String filename, String contentType);
}
