package com.secureportal.api.dto;

import com.secureportal.interview.MockInterviewQuestion;

import java.time.Instant;

/** A question with its answer and assessment. The model answer is only ever filled in once the learner has answered. */
public record InterviewQuestionDto(Long id, Long sessionId, int questionIndex, String questionText, String category, String learnerAnswer,
                                   String aiFeedback, String keyStrengths, String areasToImprove, String idealAnswer, int score,
                                   Instant answeredAt) {

    public static InterviewQuestionDto of(MockInterviewQuestion q) {
        return new InterviewQuestionDto(q.getId(), q.getSessionId(), q.getQuestionIndex(), q.getQuestionText(), q.getCategory(),
                q.getLearnerAnswer(), q.getAiFeedback(), q.getKeyStrengths(), q.getAreasToImprove(), q.getIdealAnswer(), q.getScore(),
                q.getAnsweredAt());
    }
}
