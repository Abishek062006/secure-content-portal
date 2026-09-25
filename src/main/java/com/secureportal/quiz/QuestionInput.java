package com.secureportal.quiz;

import java.util.List;

/** A question as an admin typed it in. */
public record QuestionInput(String text, String difficulty, List<String> options, int correctIndex, String explanation) {
}
