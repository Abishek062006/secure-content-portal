package com.secureportal.profile;

public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException() {
        super("That profile doesn't exist.");
    }
}
