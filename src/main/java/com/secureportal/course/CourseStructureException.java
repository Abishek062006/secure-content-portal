package com.secureportal.course;

/** The request doesn't make sense for the course's current structure (bad ordering, empty course, ...). */
public class CourseStructureException extends RuntimeException {

    public CourseStructureException(String message) {
        super(message);
    }
}
