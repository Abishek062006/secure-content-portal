package com.secureportal.common;

/** Carries a message safe to show directly to the person who tried the upload. */
public class UploadException extends RuntimeException {

    public UploadException(String message) {
        super(message);
    }
}
