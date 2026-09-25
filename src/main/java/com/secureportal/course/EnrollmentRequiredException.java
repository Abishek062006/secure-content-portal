package com.secureportal.course;

public class EnrollmentRequiredException extends RuntimeException {

    public EnrollmentRequiredException() {
        super("Enroll in this course to watch its lessons.");
    }
}
