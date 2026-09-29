package com.secureportal.course;

/** Anything that goes wrong asking for or deciding on access to a REGISTER-type course. */
public class RegistrationRequestException extends RuntimeException {

    public RegistrationRequestException(String message) {
        super(message);
    }
}
