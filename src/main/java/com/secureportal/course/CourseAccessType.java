package com.secureportal.course;

/** OPEN: enroll immediately, free or paid (payments aren't connected yet, so this is the same "one click and
 *  you're in" flow either way). REGISTER: a learner asks, an admin decides, enrollment happens on approval. */
public enum CourseAccessType {
    OPEN,
    REGISTER
}
