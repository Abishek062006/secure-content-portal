package com.secureportal.quiz;

import java.util.List;

/**
 * A question as an admin typed it in. {@code type}, {@code codeSnippet}, {@code showOptions} and {@code acceptedAnswers} are for
 * the coding types and are left out for plain multiple choice.
 */
public record QuestionInput(String text, String difficulty, List<String> options, int correctIndex, String explanation,
                            String type, String codeSnippet, boolean showOptions, List<String> acceptedAnswers) {

    public QuestionInput(String text, String difficulty, List<String> options, int correctIndex, String explanation) {
        this(text, difficulty, options, correctIndex, explanation, null, null, true, List.of());
    }

    QuestionFactory.Style style() {
        return QuestionFactory.style(type, codeSnippet, showOptions, acceptedAnswers);
    }
}
