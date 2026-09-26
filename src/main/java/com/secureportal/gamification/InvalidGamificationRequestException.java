package com.secureportal.gamification;

/** A points or rules request that can't be honoured (out-of-range amount, unknown learner, unknown rule). Shown as-is to the admin. */
public class InvalidGamificationRequestException extends RuntimeException {

    public InvalidGamificationRequestException(String message) {
        super(message);
    }
}
