package com.secureportal.network;

/** A connection request or message that can't be made: to yourself, to an admin, too many pending, or an empty message. */
public class InvalidConnectionException extends RuntimeException {

    public InvalidConnectionException(String message) {
        super(message);
    }
}
