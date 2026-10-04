package com.secureportal.network;

/** The request or person doesn't exist, or isn't the signed-in member's to act on: the two look the same from outside. */
public class ConnectionNotFoundException extends RuntimeException {

    public ConnectionNotFoundException() {
        super("We couldn't find that connection request.");
    }
}
