package com.secureportal.stream;

/** Thrown for any invalid, expired, tampered, or wrong-session ticket. */
public class TicketException extends RuntimeException {

    public TicketException(String message) {
        super(message);
    }
}
