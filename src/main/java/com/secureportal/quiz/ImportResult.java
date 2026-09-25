package com.secureportal.quiz;

import java.util.List;

/** What a CSV import did: how many questions it added, and which rows it had to skip and why. */
public record ImportResult(int imported, List<RowError> errors) {

    public record RowError(int line, String message) {
    }
}
