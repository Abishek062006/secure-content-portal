package com.secureportal.api.dto;

import com.secureportal.interview.MockInterviewQuestion;
import com.secureportal.interview.MockInterviewSession;

import java.util.List;

public record InterviewDetailDto(InterviewSessionDto session, List<InterviewQuestionDto> questions) {

    public static InterviewDetailDto of(MockInterviewSession session, List<MockInterviewQuestion> questions) {
        return new InterviewDetailDto(InterviewSessionDto.of(session), questions.stream().map(InterviewQuestionDto::of).toList());
    }
}
