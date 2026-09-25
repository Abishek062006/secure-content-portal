package com.secureportal.api.dto;

import java.util.List;
import java.util.UUID;

public record AssessmentResultsDto(List<AssessmentRow> assessments, List<HardQuestion> hardestQuestions) {

    public record AssessmentRow(UUID id, String title, String type, String scope, int attempts, int learners,
                                Integer averageScore, Integer passRatePercent) {
    }

    public record HardQuestion(UUID id, String text, String difficulty, String lessonTitle, int answered,
                               int correctPercent) {
    }
}
