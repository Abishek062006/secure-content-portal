package com.secureportal.network;

/** The two people are already connected, or a request between them is already waiting. */
public class ConnectionConflictException extends RuntimeException {

    public ConnectionConflictException(String message) {
        super(message);
    }
}
