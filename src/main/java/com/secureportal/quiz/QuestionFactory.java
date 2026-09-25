package com.secureportal.quiz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** The one place the question rules live, whether a question is typed in, imported, or written by the AI. */
public final class QuestionFactory {

    private QuestionFactory() {
    }

    /** A question's fields after validation and tidying. */
    public record Checked(String text, Difficulty difficulty, String explanation, List<QuestionOption> options) {
    }

    /**
     * @param shuffle mix the options up (used for AI questions, which tend to put the right answer first);
     *                typed and imported questions keep the order their author chose
     */
    public static Checked check(String text, String difficultyName, List<String> optionTexts, int correctIndex,
                                String explanation, boolean shuffle) {
        String questionText = text == null ? "" : text.trim();
        if (questionText.isEmpty()) {
            throw new InvalidQuestionException("The question text is required.");
        }
        if (questionText.length() > 1000) {
            throw new InvalidQuestionException("The question text must be 1000 characters or fewer.");
        }

        Difficulty difficulty;
        try {
            difficulty = Difficulty.valueOf(difficultyName == null ? "" : difficultyName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidQuestionException("Difficulty must be EASY, MEDIUM or HARD.");
        }

        if (optionTexts == null || optionTexts.size() != 4) {
            throw new InvalidQuestionException("A question needs exactly 4 options.");
        }
        if (correctIndex < 0 || correctIndex > 3) {
            throw new InvalidQuestionException("Choose which of the 4 options is correct.");
        }
        List<QuestionOption> options = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            String option = optionTexts.get(i) == null ? "" : optionTexts.get(i).trim();
            if (option.isEmpty()) {
                throw new InvalidQuestionException("Options can't be empty.");
            }
            if (option.length() > 500) {
                throw new InvalidQuestionException("Each option must be 500 characters or fewer.");
            }
            if (!seen.add(option.toLowerCase(Locale.ROOT))) {
                throw new InvalidQuestionException("Options must all be different.");
            }
            options.add(new QuestionOption(option, i == correctIndex));
        }
        if (shuffle) {
            Collections.shuffle(options);
        }

        String note = explanation == null ? "" : explanation.trim();
        if (note.length() > 1000) {
            note = note.substring(0, 1000);
        }
        return new Checked(questionText, difficulty, note.isEmpty() ? null : note, options);
    }

    public static Question build(UUID courseId, UUID lessonId, QuestionSource source, QuestionStatus status,
                                 String text, String difficultyName, List<String> optionTexts, int correctIndex,
                                 String explanation, Integer sourceSeconds, boolean shuffle) {
        Checked checked = check(text, difficultyName, optionTexts, correctIndex, explanation, shuffle);
        return new Question(courseId, lessonId, checked.text(), checked.difficulty(), checked.explanation(),
                sourceSeconds, source, status, checked.options());
    }
}
