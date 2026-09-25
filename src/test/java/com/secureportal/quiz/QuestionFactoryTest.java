package com.secureportal.quiz;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionFactoryTest {

    private static final List<String> OPTIONS = List.of("A", "B", "C", "D");

    @Test
    void acceptsAValidQuestionAndKeepsTheAuthorsOrder() {
        QuestionFactory.Checked checked = QuestionFactory.check("  Why?  ", "medium", OPTIONS, 2, " Because. ", false);

        assertThat(checked.text()).isEqualTo("Why?");
        assertThat(checked.difficulty()).isEqualTo(Difficulty.MEDIUM);
        assertThat(checked.explanation()).isEqualTo("Because.");
        assertThat(checked.options()).extracting(QuestionOption::getText).containsExactly("A", "B", "C", "D");
        assertThat(checked.options()).extracting(QuestionOption::isCorrect).containsExactly(false, false, true, false);
    }

    @Test
    void shufflingNeverLosesTheCorrectAnswer() {
        for (int i = 0; i < 25; i++) {
            QuestionFactory.Checked checked = QuestionFactory.check("Q?", "EASY", OPTIONS, 1, null, true);

            assertThat(checked.options().stream().filter(QuestionOption::isCorrect).map(QuestionOption::getText))
                    .containsExactly("B");
        }
    }

    @Test
    void rejectsEachBrokenRule() {
        assertRejected("", "EASY", OPTIONS, 0, "text is required");
        assertRejected("Q?", "IMPOSSIBLE", OPTIONS, 0, "EASY, MEDIUM or HARD");
        assertRejected("Q?", "EASY", List.of("A", "B", "C"), 0, "exactly 4");
        assertRejected("Q?", "EASY", OPTIONS, 4, "which of the 4");
        assertRejected("Q?", "EASY", List.of("A", "", "C", "D"), 0, "can't be empty");
        assertRejected("Q?", "EASY", List.of("A", "a", "C", "D"), 0, "different");
        assertRejected("x".repeat(1001), "EASY", OPTIONS, 0, "1000 characters");
    }

    private static void assertRejected(String text, String difficulty, List<String> options, int correct, String message) {
        assertThatThrownBy(() -> QuestionFactory.check(text, difficulty, options, correct, null, false))
                .isInstanceOf(InvalidQuestionException.class)
                .hasMessageContaining(message);
    }
}
