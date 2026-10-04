package com.secureportal.quiz;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TypedAnswerTest {

    private static Question question(QuestionType type, String correct, List<String> alsoAccept) {
        QuestionFactory.Style style = new QuestionFactory.Style(type, "print(1)", false, alsoAccept);
        return new Question(UUID.randomUUID(), UUID.randomUUID(), "What?", Difficulty.EASY, null, null, QuestionSource.MANUAL,
                QuestionStatus.APPROVED, List.of(new QuestionOption(correct, true), new QuestionOption("w1", false),
                        new QuestionOption("w2", false), new QuestionOption("w3", false)), null, style);
    }

    @Test
    void ignoresSpacingAndQuoteStyleInCode() {
        Question fill = question(QuestionType.FILL_CODE, "df.groupby('city')['sales'].sum()", List.of());

        assertThat(TypedAnswer.matches(fill, "df.groupby('city')['sales'].sum()")).isTrue();
        assertThat(TypedAnswer.matches(fill, "  df.groupby( \"city\" )[ \"sales\" ].sum( )  ")).isTrue();
        assertThat(TypedAnswer.matches(fill, "df . groupby('city')['sales'].sum()")).isTrue();
        assertThat(TypedAnswer.matches(fill, "df.groupby('city')['sales'].mean()")).isFalse();
    }

    @Test
    void codeIsCaseSensitiveButOutputIsNot() {
        Question fill = question(QuestionType.FILL_CODE, "df", List.of());
        Question output = question(QuestionType.PREDICT_OUTPUT, "True", List.of());

        assertThat(TypedAnswer.matches(fill, "DF")).isFalse();
        assertThat(TypedAnswer.matches(output, "true")).isTrue();
    }

    @Test
    void keepsWordsApartAndTreatsLinesAsOneSpacedLine() {
        Question output = question(QuestionType.PREDICT_OUTPUT, "3\n4", List.of());

        assertThat(TypedAnswer.matches(output, "3 4")).isTrue();
        assertThat(TypedAnswer.matches(output, "3\r\n4")).isTrue();
        assertThat(TypedAnswer.matches(output, "34")).isFalse();
    }

    @Test
    void anExtraAcceptedAnswerCountsAsRight() {
        Question fill = question(QuestionType.FILL_CODE, "dropna", List.of("dropna()", "df.dropna()"));

        assertThat(TypedAnswer.matches(fill, "dropna")).isTrue();
        assertThat(TypedAnswer.matches(fill, "dropna ( )")).isTrue();
        assertThat(TypedAnswer.matches(fill, "df.dropna()")).isTrue();
        assertThat(TypedAnswer.matches(fill, "fillna")).isFalse();
    }

    @Test
    void aBlankAnswerIsNeverRight() {
        Question fill = question(QuestionType.FILL_CODE, "x", List.of());

        assertThat(TypedAnswer.matches(fill, null)).isFalse();
        assertThat(TypedAnswer.matches(fill, "   ")).isFalse();
    }
}
