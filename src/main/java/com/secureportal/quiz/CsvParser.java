package com.secureportal.quiz;

import java.util.ArrayList;
import java.util.List;

/** A small RFC 4180 reader: quoted fields, doubled quotes, commas and line breaks inside quotes. */
public final class CsvParser {

    private CsvParser() {
    }

    /** One row plus the line it started on, so import errors can point at the right place. */
    public record Row(int line, List<String> cells) {
    }

    public static List<Row> parse(String text) {
        String input = text.replace("﻿", "");
        List<Row> rows = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean quoted = false;
        int line = 1;
        int rowLine = 1;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < input.length() && input.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    field.append(c);
                }
            } else if (c == '"' && field.isEmpty() && !quoted) {
                inQuotes = true;
                quoted = true;
            } else if (c == ',') {
                cells.add(field.toString());
                field.setLength(0);
                quoted = false;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') {
                    i++;
                }
                cells.add(field.toString());
                addRow(rows, cells, rowLine);
                cells = new ArrayList<>();
                field.setLength(0);
                quoted = false;
                line++;
                rowLine = line;
            } else {
                field.append(c);
            }
        }
        if (!field.isEmpty() || !cells.isEmpty() || quoted) {
            cells.add(field.toString());
            addRow(rows, cells, rowLine);
        }
        return rows;
    }

    private static void addRow(List<Row> rows, List<String> cells, int line) {
        boolean blank = cells.stream().allMatch(cell -> cell.trim().isEmpty());
        if (!blank) {
            rows.add(new Row(line, cells));
        }
    }
}
