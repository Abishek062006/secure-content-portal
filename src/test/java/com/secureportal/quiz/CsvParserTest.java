package com.secureportal.quiz;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvParserTest {

    @Test
    void readsPlainRowsAndRecordsTheirLines() {
        List<CsvParser.Row> rows = CsvParser.parse("a,b,c\n1,2,3\n");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).cells()).containsExactly("1", "2", "3");
        assertThat(rows.get(1).line()).isEqualTo(2);
    }

    @Test
    void handlesQuotedCommasDoubledQuotesAndLineBreaksInsideQuotes() {
        List<CsvParser.Row> rows = CsvParser.parse("q,note\n\"What is 1,2?\",\"He said \"\"hi\"\"\"\n\"two\nlines\",x\nlast,row\n");

        assertThat(rows.get(1).cells()).containsExactly("What is 1,2?", "He said \"hi\"");
        assertThat(rows.get(2).cells()).containsExactly("two\nlines", "x");
        assertThat(rows.get(3).line()).as("a quoted line break still counts as a line").isEqualTo(5);
    }

    @Test
    void toleratesCrlfBomBlankLinesAndAMissingFinalNewline() {
        List<CsvParser.Row> rows = CsvParser.parse("﻿a,b\r\n\r\n1,2");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).cells()).containsExactly("a", "b");
        assertThat(rows.get(1).cells()).containsExactly("1", "2");
    }

    @Test
    void keepsEmptyCellsInPlace() {
        assertThat(CsvParser.parse("a,,c\n").get(0).cells()).containsExactly("a", "", "c");
    }
}
