package com.secureportal.hackathon;

public class HackathonNotFoundException extends RuntimeException {

    public HackathonNotFoundException() {
        super("That hackathon doesn't exist.");
    }
}
