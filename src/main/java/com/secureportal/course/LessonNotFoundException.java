package com.secureportal.course;

import java.util.UUID;

public class LessonNotFoundException extends RuntimeException {

    public LessonNotFoundException(UUID id) {
        super("A lesson was not found: " + id);
    }
}
