package com.secureportal.quiz;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

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

    @Test
    void buildParsesTranscriptGroundingButNeverRejectsAQuestionOverIt() {
        UUID courseId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();

        Question direct = QuestionFactory.build(courseId, lessonId, QuestionSource.AI, QuestionStatus.DRAFT,
                "Q?", "EASY", OPTIONS, 0, null, null, false, "direct");
        assertThat(direct.getGrounding()).isEqualTo(TranscriptGrounding.DIRECT);

        Question related = QuestionFactory.build(courseId, lessonId, QuestionSource.AI, QuestionStatus.DRAFT,
                "Q?", "EASY", OPTIONS, 0, null, null, false, "RELATED");
        assertThat(related.getGrounding()).isEqualTo(TranscriptGrounding.RELATED);

        Question garbled = QuestionFactory.build(courseId, lessonId, QuestionSource.AI, QuestionStatus.DRAFT,
                "Q?", "EASY", OPTIONS, 0, null, null, false, "not-a-real-value");
        assertThat(garbled.getGrounding()).isNull();

        Question manual = QuestionFactory.build(courseId, lessonId, QuestionSource.MANUAL, QuestionStatus.APPROVED,
                "Q?", "EASY", OPTIONS, 0, null, null, false);
        assertThat(manual.getGrounding()).isNull();
    }

    private static void assertRejected(String text, String difficulty, List<String> options, int correct, String message) {
        assertThatThrownBy(() -> QuestionFactory.check(text, difficulty, options, correct, null, false))
                .isInstanceOf(InvalidQuestionException.class)
                .hasMessageContaining(message);
    }

    @Test
    void multipleChoiceIgnoresAnyCodeFieldsSentWithIt() {
        QuestionFactory.Style style = QuestionFactory.style("MULTIPLE_CHOICE", "print(1)", false, List.of("x"));

        assertThat(style.type()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(style.codeSnippet()).isNull();
        assertThat(style.showOptions()).isTrue();
        assertThat(style.acceptedAnswers()).isEmpty();
    }

    @Test
    void aCodingQuestionKeepsItsCodeAndTidiesTheAcceptedAnswers() {
        QuestionFactory.Style style = QuestionFactory.style("predict_output", "\r\n\nprint(2 + 2)  \n\n", false,
                java.util.Arrays.asList(" 4 ", "4", "", null, "four"));

        assertThat(style.type()).isEqualTo(QuestionType.PREDICT_OUTPUT);
        assertThat(style.codeSnippet()).isEqualTo("print(2 + 2)");
        assertThat(style.showOptions()).isFalse();
        assertThat(style.acceptedAnswers()).containsExactly("4", "four");
    }

    @Test
    void aFillTheCodeQuestionNeedsExactlyOneBlank() {
        assertThat(QuestionFactory.style("FILL_CODE", "df = df.____()", false, null).type()).isEqualTo(QuestionType.FILL_CODE);

        assertStyleRejected("FILL_CODE", "df = df.dropna()", "exactly one blank");
        assertStyleRejected("FILL_CODE", "x = ____ + ____", "exactly one blank");
    }

    @Test
    void codingQuestionsRejectMissingOrOversizedCodeAndUnknownTypes() {
        assertStyleRejected("PREDICT_OUTPUT", "  ", "Add the code");
        assertStyleRejected("PREDICT_OUTPUT", "x".repeat(4001), "4000 characters");
        assertStyleRejected("ESSAY", "print(1)", "MULTIPLE_CHOICE, FILL_CODE or PREDICT_OUTPUT");
        assertThatThrownBy(() -> QuestionFactory.style("PREDICT_OUTPUT", "print(1)", false, java.util.Collections.nCopies(11, "a")
                .stream().map(a -> a + java.util.UUID.randomUUID()).toList()))
                .isInstanceOf(InvalidQuestionException.class).hasMessageContaining("at most 10");
    }

    private static void assertStyleRejected(String type, String code, String message) {
        assertThatThrownBy(() -> QuestionFactory.style(type, code, false, null))
                .isInstanceOf(InvalidQuestionException.class).hasMessageContaining(message);
    }
}
