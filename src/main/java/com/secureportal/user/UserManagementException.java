package com.secureportal.user;

/** Carries a message safe to show directly to the admin who tried the action. */
public class UserManagementException extends RuntimeException {

    public UserManagementException(String message) {
        super(message);
    }
}
