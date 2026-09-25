package com.secureportal.quiz;

import java.util.UUID;

public class QuestionNotFoundException extends RuntimeException {

    public QuestionNotFoundException(UUID id) {
        super("Question not found: " + id);
    }
}
