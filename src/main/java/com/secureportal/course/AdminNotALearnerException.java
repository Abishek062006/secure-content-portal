package com.secureportal.course;

/** Admins run the platform; enrolling, taking quizzes and earning certificates are for learners. */
public class AdminNotALearnerException extends RuntimeException {

    public AdminNotALearnerException() {
        super("Admins manage courses and don't take them. Sign in with a learner account to enroll or earn certificates.");
    }
}
