package com.secureportal.interview;

import java.util.Optional;

/**
 * How an answer was given, as measured by the learner's browser: how long they thought, and for a spoken answer their pace and
 * pauses. It is feedback only and never part of the score. The browser is not trusted: {@link #measured()} keeps only what is
 * plausible, and a value that can't be right is dropped rather than shown as fact.
 */
public record AnswerDelivery(String mode, Double thinkingSeconds, Double speakingSeconds, Double wordsPerMinute, Double longPauses,
                             Double longestPauseSeconds, Boolean audioClear) {

    static final int MAX_THINKING_SECONDS = 600;
    static final int MAX_SPEAKING_SECONDS = 600;
    static final int MAX_PAUSES = 100;
    /** Below this much speech a pace says little. */
    static final int MIN_SPEAKING_FOR_PACE = 5;
    /** Slower or faster than this is a measuring mistake, not a way of speaking. */
    static final int MIN_PACE = 30;
    static final int MAX_PACE = 350;

    /** What is kept: the same fields, checked and rounded. */
    public record Measured(AnswerMode mode, Integer thinkingSeconds, Integer speakingSeconds, Integer wordsPerMinute, Integer longPauses,
                           Integer longestPauseMs, Boolean audioClear) {
    }

    /**
     * Empty when nothing usable was reported. A typed answer keeps only the thinking time. A spoken answer keeps its pace and pauses
     * only when the browser says the audio was clear enough to measure, and a pace only when there was enough speech behind it.
     */
    public Optional<Measured> measured() {
        Optional<AnswerMode> parsed = AnswerMode.parse(mode);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        Integer thinking = clamp(thinkingSeconds, 0, MAX_THINKING_SECONDS);

        if (parsed.get() == AnswerMode.TYPED) {
            return thinking == null ? Optional.empty() : Optional.of(new Measured(AnswerMode.TYPED, thinking, null, null, null, null, null));
        }

        boolean clear = Boolean.TRUE.equals(audioClear);
        if (!clear) {
            return Optional.of(new Measured(AnswerMode.VOICE, thinking, null, null, null, null, false));
        }
        Integer speaking = clamp(speakingSeconds, 0, MAX_SPEAKING_SECONDS);
        Integer pace = clamp(wordsPerMinute, 0, Integer.MAX_VALUE);
        if (speaking == null || speaking < MIN_SPEAKING_FOR_PACE || pace == null || pace < MIN_PACE || pace > MAX_PACE) {
            pace = null;
        }
        Integer pauses = clamp(longPauses, 0, MAX_PAUSES);
        Integer longest = clamp(longestPauseSeconds == null ? null : longestPauseSeconds * 1000, 0, MAX_SPEAKING_SECONDS * 1000);
        return Optional.of(new Measured(AnswerMode.VOICE, thinking, speaking, pace, pauses, longest, true));
    }

    private static Integer clamp(Double value, int min, int max) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return null;
        }
        return (int) Math.max(min, Math.min(max, Math.round(value)));
    }
}
