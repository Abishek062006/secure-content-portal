package com.secureportal.hackathon;

/** A hackathon an admin submitted that isn't acceptable (missing or over-long text, a link that isn't https, dates out of order). */
public class InvalidHackathonException extends RuntimeException {

    public InvalidHackathonException(String message) {
        super(message);
    }
}
