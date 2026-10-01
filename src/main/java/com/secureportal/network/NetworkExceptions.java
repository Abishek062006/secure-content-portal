package com.secureportal.network;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public class NetworkExceptions {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class SelfConnectionException extends RuntimeException {
        public SelfConnectionException() {
            super("You cannot send a connection request to yourself.");
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class DuplicateConnectionException extends RuntimeException {
        public DuplicateConnectionException(String message) {
            super(message);
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class ConnectionNotFoundException extends RuntimeException {
        public ConnectionNotFoundException() {
            super("Connection request or record not found.");
        }
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class ConnectionAccessDeniedException extends RuntimeException {
        public ConnectionAccessDeniedException(String message) {
            super(message);
        }
    }
}
