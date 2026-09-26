package com.secureportal.hackathon;

/** Registration is over: the hackathon has finished or its deadline has passed. */
public class HackathonClosedException extends RuntimeException {

    public HackathonClosedException() {
        super("Registration for this hackathon has closed.");
    }
}
