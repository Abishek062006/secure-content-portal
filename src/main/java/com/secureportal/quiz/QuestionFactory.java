package com.secureportal.quiz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The one place the question rules live, whether a question is typed in, imported, or written by the AI. */
public final class QuestionFactory {

    static final int MAX_CODE = 4000;
    static final int MAX_ACCEPTED = 10;
    /** The gap in a fill-the-code snippet: three or more underscores in a row. */
    private static final Pattern BLANK = Pattern.compile("_{3,}");

    private QuestionFactory() {
    }

    /**
     * What sets a coding question apart from plain multiple choice. {@code showOptions} is the admin's choice for the coding
     * types: show the options, or have learners type the answer.
     */
    public record Style(QuestionType type, String codeSnippet, boolean showOptions, List<String> acceptedAnswers) {
        public static Style choice() {
            return new Style(QuestionType.MULTIPLE_CHOICE, null, true, List.of());
        }
    }

    /** A question's fields after validation and tidying. */
    public record Checked(String text, Difficulty difficulty, String explanation, List<QuestionOption> options, Style style) {
    }

    /**
     * Validates the coding side of a question. Multiple choice ignores everything but its type; the coding types need a snippet
     * (a fill-the-code one with exactly one blank) and may list extra answers a typed response can match.
     */
    public static Style style(String typeName, String codeSnippet, boolean showOptions, List<String> acceptedAnswers) {
        QuestionType type = typeName == null || typeName.isBlank() ? QuestionType.MULTIPLE_CHOICE
                : QuestionType.parse(typeName).orElseThrow(() -> new InvalidQuestionException(
                        "The question type must be MULTIPLE_CHOICE, FILL_CODE or PREDICT_OUTPUT."));
        if (!type.isCode()) {
            return Style.choice();
        }

        String code = codeSnippet == null ? "" : codeSnippet.replace("\r\n", "\n").replaceAll("^(\\s*\\n)+", "").stripTrailing();
        if (code.isBlank()) {
            throw new InvalidQuestionException("Add the code this question is about.");
        }
        if (code.length() > MAX_CODE) {
            throw new InvalidQuestionException("The code must be " + MAX_CODE + " characters or fewer.");
        }
        if (type == QuestionType.FILL_CODE) {
            Matcher blanks = BLANK.matcher(code);
            int count = 0;
            while (blanks.find()) {
                count++;
            }
            if (count != 1) {
                throw new InvalidQuestionException("A fill-the-code question needs exactly one blank, marked with ____ (four underscores).");
            }
        }

        List<String> accepted = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (acceptedAnswers != null) {
            for (String raw : acceptedAnswers) {
                String answer = raw == null ? "" : raw.strip();
                if (answer.isEmpty() || !seen.add(answer.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                if (answer.length() > 500) {
                    throw new InvalidQuestionException("Each accepted answer must be 500 characters or fewer.");
                }
                accepted.add(answer);
            }
        }
        if (accepted.size() > MAX_ACCEPTED) {
            throw new InvalidQuestionException("List at most " + MAX_ACCEPTED + " extra accepted answers.");
        }
        return new Style(type, code, showOptions, List.copyOf(accepted));
    }

    /**
     * @param shuffle mix the options up (used for AI questions, which tend to put the right answer first);
     *                typed and imported questions keep the order their author chose
     */
    public static Checked check(String text, String difficultyName, List<String> optionTexts, int correctIndex,
                                String explanation, boolean shuffle) {
        return check(text, difficultyName, optionTexts, correctIndex, explanation, shuffle, Style.choice());
    }

    public static Checked check(String text, String difficultyName, List<String> optionTexts, int correctIndex,
                                String explanation, boolean shuffle, Style style) {
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
        return new Checked(questionText, difficulty, note.isEmpty() ? null : note, options, style);
    }

    public static Question build(UUID courseId, UUID lessonId, QuestionSource source, QuestionStatus status,
                                 String text, String difficultyName, List<String> optionTexts, int correctIndex,
                                 String explanation, Integer sourceSeconds, boolean shuffle) {
        return build(courseId, lessonId, source, status, text, difficultyName, optionTexts, correctIndex,
                explanation, sourceSeconds, shuffle, null);
    }

    /** As above, plus how the AI generator classified the question against its source transcript — null for a
     *  manually typed or imported one, since there's nothing to classify it against. */
    public static Question build(UUID courseId, UUID lessonId, QuestionSource source, QuestionStatus status,
                                 String text, String difficultyName, List<String> optionTexts, int correctIndex,
                                 String explanation, Integer sourceSeconds, boolean shuffle, String groundingName) {
        return build(courseId, lessonId, source, status, text, difficultyName, optionTexts, correctIndex,
                explanation, sourceSeconds, shuffle, groundingName, Style.choice());
    }

    /** As above, for a coding question: {@code style} comes from {@link #style}. */
    public static Question build(UUID courseId, UUID lessonId, QuestionSource source, QuestionStatus status,
                                 String text, String difficultyName, List<String> optionTexts, int correctIndex,
                                 String explanation, Integer sourceSeconds, boolean shuffle, String groundingName, Style style) {
        Checked checked = check(text, difficultyName, optionTexts, correctIndex, explanation, shuffle, style);
        return new Question(courseId, lessonId, checked.text(), checked.difficulty(), checked.explanation(),
                sourceSeconds, source, status, checked.options(), parseGrounding(groundingName), checked.style());
    }

    /** Lenient on purpose: a question is never dropped just because the model's grounding tag was missing
     *  or garbled — it's a filter aid, not something worth losing an otherwise-valid question over. */
    private static TranscriptGrounding parseGrounding(String groundingName) {
        if (groundingName == null || groundingName.isBlank()) {
            return null;
        }
        try {
            return TranscriptGrounding.valueOf(groundingName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
