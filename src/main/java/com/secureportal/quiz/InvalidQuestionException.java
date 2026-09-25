package com.secureportal.quiz;

/** A question (typed in, imported, or written by the model) breaks one of the rules. */
public class InvalidQuestionException extends RuntimeException {

    public InvalidQuestionException(String message) {
        super(message);
    }
}
