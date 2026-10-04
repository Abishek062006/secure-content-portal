package com.secureportal.api.dto;

import com.secureportal.quiz.Question;

import java.util.List;
import java.util.UUID;

/** The admin view of a question — includes which option is correct. */
public record QuestionDto(
        UUID id,
        UUID courseId,
        UUID lessonId,
        String lessonTitle,
        String text,
        String difficulty,
        String explanation,
        Integer sourceSeconds,
        String status,
        String source,
        String grounding,
        boolean finalOnly,
        String type,
        String codeSnippet,
        boolean showOptions,
        List<String> acceptedAnswers,
        List<Option> options
) {
    public record Option(String text, boolean correct) {
    }

    public static QuestionDto from(Question question, String lessonTitle) {
        return new QuestionDto(
                question.getId(),
                question.getCourseId(),
                question.getLessonId(),
                lessonTitle,
                question.getText(),
                question.getDifficulty().name(),
                question.getExplanation(),
                question.getSourceSeconds(),
                question.getStatus().name(),
                question.getSource().name(),
                question.getGrounding() == null ? null : question.getGrounding().name(),
                question.isFinalOnly(),
                question.getType().name(),
                question.getCodeSnippet(),
                question.isShowOptions(),
                List.copyOf(question.getAcceptedAnswers()),
                question.getOptions().stream().map(o -> new Option(o.getText(), o.isCorrect())).toList()
        );
    }
}
