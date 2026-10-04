package com.secureportal.api.dto;

import com.secureportal.interview.MockInterviewQuestion;

import java.time.Instant;

/** A question with its answer and assessment. The model answer is only ever filled in once the learner has answered. */
public record InterviewQuestionDto(Long id, Long sessionId, int questionIndex, String questionText, String category, String learnerAnswer,
                                   String aiFeedback, String keyStrengths, String areasToImprove, String idealAnswer, int score,
                                   Instant answeredAt, Long parentQuestionId, Integer relevance, Integer depth, Integer structure,
                                   Integer communication, Delivery delivery) {

    /** How the answer was given: feedback for the learner only, so it is left out of what admins see. */
    public record Delivery(String mode, Integer thinkingSeconds, Integer speakingSeconds, Integer wordsPerMinute, Integer longPauses,
                           Double longestPauseSeconds, Boolean audioClear) {
    }

    public static InterviewQuestionDto of(MockInterviewQuestion q) {
        return build(q, deliveryOf(q));
    }

    /** The same, without the delivery measurements. */
    public static InterviewQuestionDto withoutDelivery(MockInterviewQuestion q) {
        return build(q, null);
    }

    private static InterviewQuestionDto build(MockInterviewQuestion q, Delivery delivery) {
        return new InterviewQuestionDto(q.getId(), q.getSessionId(), q.getQuestionIndex(), q.getQuestionText(), q.getCategory(),
                q.getLearnerAnswer(), q.getAiFeedback(), q.getKeyStrengths(), q.getAreasToImprove(), q.getIdealAnswer(), q.getScore(),
                q.getAnsweredAt(), q.getParentQuestionId(), q.getRelevanceScore(), q.getDepthScore(), q.getStructureScore(),
                q.getCommunicationScore(), delivery);
    }

    private static Delivery deliveryOf(MockInterviewQuestion q) {
        if (!q.hasDelivery()) {
            return null;
        }
        Double longest = q.getLongestPauseMs() == null ? null : Math.round(q.getLongestPauseMs() / 100.0) / 10.0;
        return new Delivery(q.getAnswerMode().name(), q.getThinkingSeconds(), q.getSpeakingSeconds(), q.getWordsPerMinute(),
                q.getLongPauses(), longest, q.getAudioClear());
    }
}
