package com.secureportal.quiz;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Comparing what a learner typed with what a coding question expects. There is no code execution: an answer is right when it
 * says the same thing as the correct option or one of the extra accepted answers, ignoring spacing and quote style. Code
 * is case-sensitive (a variable named {@code df} is not {@code DF}); output is not, since "True" and "true" read the same.
 */
public final class TypedAnswer {

    /** Spacing next to these never changes what a snippet means: {@code x = 5}, {@code x=5} and {@code x= 5} agree. */
    private static final Pattern AROUND_SYMBOLS = Pattern.compile("\\s*([(),=:\\[\\]{}.+\\-*/<>%!&|^~;])\\s*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TypedAnswer() {
    }

    /** The comparable form of an answer: tidy spacing and one kind of quote. */
    static String normalize(String answer, boolean ignoreCase) {
        if (answer == null) {
            return "";
        }
        String text = WHITESPACE.matcher(answer.replace('"', '\'').strip()).replaceAll(" ");
        text = AROUND_SYMBOLS.matcher(text).replaceAll("$1");
        return ignoreCase ? text.toLowerCase(Locale.ROOT) : text;
    }

    public static boolean matches(Question question, String typed) {
        if (typed == null || typed.isBlank()) {
            return false;
        }
        boolean ignoreCase = question.getType() == QuestionType.PREDICT_OUTPUT;
        String given = normalize(typed, ignoreCase);
        return question.expectedAnswers().stream().anyMatch(expected -> given.equals(normalize(expected, ignoreCase)));
    }
}
