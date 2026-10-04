package com.secureportal.interview;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerDeliveryTest {

    private static AnswerDelivery voice(Double thinking, Double speaking, Double pace, Double pauses, Double longest, Boolean clear) {
        return new AnswerDelivery("VOICE", thinking, speaking, pace, pauses, longest, clear);
    }

    @Test
    void aClearSpokenAnswerKeepsItsTimingRoundedToWholeNumbers() {
        AnswerDelivery.Measured m = voice(7.4, 62.6, 148.2, 2.0, 3.46, true).measured().orElseThrow();

        assertThat(m.mode()).isEqualTo(AnswerMode.VOICE);
        assertThat(m.thinkingSeconds()).isEqualTo(7);
        assertThat(m.speakingSeconds()).isEqualTo(63);
        assertThat(m.wordsPerMinute()).isEqualTo(148);
        assertThat(m.longPauses()).isEqualTo(2);
        assertThat(m.longestPauseMs()).isEqualTo(3460);
        assertThat(m.audioClear()).isTrue();
    }

    @Test
    void aTypedAnswerKeepsOnlyTheThinkingTimeWhateverElseIsSent() {
        AnswerDelivery.Measured m = new AnswerDelivery("typed", 12.0, 40.0, 150.0, 3.0, 5.0, true).measured().orElseThrow();

        assertThat(m.mode()).isEqualTo(AnswerMode.TYPED);
        assertThat(m.thinkingSeconds()).isEqualTo(12);
        assertThat(m.speakingSeconds()).isNull();
        assertThat(m.wordsPerMinute()).isNull();
        assertThat(m.longPauses()).isNull();
        assertThat(m.longestPauseMs()).isNull();
        assertThat(m.audioClear()).isNull();
    }

    @Test
    void aTypedAnswerWithNoThinkingTimeHasNothingWorthKeeping() {
        assertThat(new AnswerDelivery("TYPED", null, null, null, null, null, null).measured()).isEmpty();
    }

    @Test
    void whenTheAudioWasNotClearOnlyThinkingTimeSurvivesAndItIsFlagged() {
        for (Boolean clear : new Boolean[] {false, null}) {
            AnswerDelivery.Measured m = voice(9.0, 50.0, 150.0, 2.0, 4.0, clear).measured().orElseThrow();

            assertThat(m.thinkingSeconds()).isEqualTo(9);
            assertThat(m.audioClear()).isFalse();
            assertThat(m.speakingSeconds()).isNull();
            assertThat(m.wordsPerMinute()).isNull();
            assertThat(m.longPauses()).isNull();
            assertThat(m.longestPauseMs()).isNull();
        }
    }

    @Test
    void aPaceThatCannotBeRightOrRestsOnTooLittleSpeechIsDroppedButThePausesStay() {
        assertThat(voice(1.0, 40.0, 900.0, 1.0, 2.0, true).measured().orElseThrow().wordsPerMinute()).as("far too fast").isNull();
        assertThat(voice(1.0, 40.0, 5.0, 1.0, 2.0, true).measured().orElseThrow().wordsPerMinute()).as("far too slow").isNull();
        AnswerDelivery.Measured brief = voice(1.0, 3.0, 150.0, 1.0, 2.0, true).measured().orElseThrow();
        assertThat(brief.wordsPerMinute()).as("under five seconds of speech").isNull();
        assertThat(brief.longPauses()).isEqualTo(1);
        assertThat(voice(1.0, null, 150.0, 1.0, 2.0, true).measured().orElseThrow().wordsPerMinute()).as("no speaking time").isNull();
    }

    @Test
    void absurdNumbersAreClampedAndNonNumbersAreIgnored() {
        AnswerDelivery.Measured m = voice(-30.0, 99999.0, 150.0, 5000.0, 99999.0, true).measured().orElseThrow();

        assertThat(m.thinkingSeconds()).isZero();
        assertThat(m.speakingSeconds()).isEqualTo(AnswerDelivery.MAX_SPEAKING_SECONDS);
        assertThat(m.longPauses()).isEqualTo(AnswerDelivery.MAX_PAUSES);
        assertThat(m.longestPauseMs()).isEqualTo(AnswerDelivery.MAX_SPEAKING_SECONDS * 1000);
        assertThat(voice(Double.NaN, 40.0, 150.0, 1.0, 2.0, true).measured().orElseThrow().thinkingSeconds()).isNull();
        assertThat(voice(Double.POSITIVE_INFINITY, 40.0, 150.0, 1.0, 2.0, true).measured().orElseThrow().thinkingSeconds()).isNull();
        assertThat(voice(100000.0, 40.0, 150.0, 1.0, 2.0, true).measured().orElseThrow().thinkingSeconds()).isEqualTo(AnswerDelivery.MAX_THINKING_SECONDS);
    }

    @Test
    void anUnknownOrMissingModeMeansNothingWasReported() {
        assertThat(new AnswerDelivery("TELEPATHY", 5.0, null, null, null, null, null).measured()).isEmpty();
        assertThat(new AnswerDelivery(null, 5.0, null, null, null, null, null).measured()).isEmpty();
        assertThat(new AnswerDelivery("  ", 5.0, null, null, null, null, null).measured()).isEmpty();
    }
}
