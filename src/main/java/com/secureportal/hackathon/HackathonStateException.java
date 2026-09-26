package com.secureportal.hackathon;

/** The request is fine but doesn't fit where the event is right now, or what already exists (wrong phase, team full, name taken). */
public class HackathonStateException extends RuntimeException {

    public HackathonStateException(String message) {
        super(message);
    }
}
