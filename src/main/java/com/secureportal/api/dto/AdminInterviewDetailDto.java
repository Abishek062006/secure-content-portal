package com.secureportal.api.dto;

import com.secureportal.interview.MockInterviewService;

import java.util.List;

public record AdminInterviewDetailDto(InterviewSessionDto session, List<InterviewQuestionDto> questions, String candidateName,
                                      String candidateEmail) {

    public static AdminInterviewDetailDto of(MockInterviewService.AdminDetail d) {
        return new AdminInterviewDetailDto(InterviewSessionDto.of(d.session()), d.questions().stream().map(InterviewQuestionDto::of).toList(),
                d.candidateName(), d.candidateEmail());
    }
}
