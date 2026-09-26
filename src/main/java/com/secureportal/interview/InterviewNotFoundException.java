package com.secureportal.interview;

/** No such interview, or it belongs to someone else. Deliberately the same answer for both, so ids can't be probed. */
public class InterviewNotFoundException extends RuntimeException {

    public InterviewNotFoundException() {
        super("That interview doesn't exist.");
    }
}
