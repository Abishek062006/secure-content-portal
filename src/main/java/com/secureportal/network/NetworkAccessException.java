package com.secureportal.network;

/** Messaging is only for people who have accepted each other's connection. */
public class NetworkAccessException extends RuntimeException {

    public NetworkAccessException(String message) {
        super(message);
    }
}
