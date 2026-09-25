package com.secureportal.api;

import com.secureportal.api.dto.AssessmentSummaryDto;
import com.secureportal.api.dto.AttemptDto;
import com.secureportal.api.dto.AttemptDto.QuestionView;
import com.secureportal.assessment.Assessment;
import com.secureportal.assessment.AssessmentService;
import com.secureportal.assessment.Attempt;
import com.secureportal.assessment.AttemptQuestion;
import com.secureportal.assessment.AttemptService;
import com.secureportal.assessment.AttemptStatus;
import com.secureportal.quiz.Difficulty;
import com.secureportal.quiz.Question;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Builds the JSON for assessments and attempts — including deciding what an unfinished attempt must not reveal. */
@Component
public class AssessmentAssembler {

    private final AssessmentService assessmentService;

    public AssessmentAssembler(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    public AssessmentSummaryDto admin(Assessment a) {
        Map<Difficulty, Long> available = assessmentService.available(a);
        return build(a, null, null, null, null, null, null, null,
                available.get(Difficulty.EASY).intValue(), available.get(Difficulty.MEDIUM).intValue(),
                available.get(Difficulty.HARD).intValue());
    }

    /** {@code lockReason} is present when the learner can't start it yet; {@code mine} is all their attempts at it. */
    public AssessmentSummaryDto learner(Assessment a, List<Attempt> mine, Optional<String> lockReason) {
        List<Attempt> submitted = mine.stream().filter(x -> x.getStatus() == AttemptStatus.SUBMITTED).toList();
        Attempt inProgress = mine.stream().filter(x -> x.getStatus() == AttemptStatus.IN_PROGRESS).findFirst().orElse(null);
        Integer best = submitted.stream().map(Attempt::getScorePercent).filter(Objects::nonNull).max(Integer::compare).orElse(null);
        boolean passed = submitted.stream().anyMatch(x -> Boolean.TRUE.equals(x.getPassed()));
        int used = mine.size();
        Integer left = a.isGraded() && a.getMaxAttempts() != null ? Math.max(0, a.getMaxAttempts() - used) : null;

        String status;
        if (passed) {
            status = "PASSED";
        } else if (inProgress != null) {
            status = "IN_PROGRESS";
        } else if (lockReason.isPresent()) {
            status = "LOCKED";
        } else if (left != null && left == 0) {
            status = "EXHAUSTED";
        } else {
            status = "AVAILABLE";
        }
        return build(a, status, lockReason.filter(r -> !passed && inProgress == null).orElse(null), used, left, best,
                a.isGraded() ? passed : null, inProgress == null ? null : inProgress.getId(), null, null, null);
    }

    private AssessmentSummaryDto build(Assessment a, String status, String lockedReason, Integer used, Integer left,
                                       Integer best, Boolean passed, java.util.UUID inProgressId,
                                       Integer availableEasy, Integer availableMedium, Integer availableHard) {
        return new AssessmentSummaryDto(a.getId(), a.getModuleId(), a.getType().name(), a.getTitle(),
                a.getEasyCount(), a.getMediumCount(), a.getHardCount(), a.totalQuestions(), a.getPassPercent(),
                a.getTimeLimitMinutes(), a.getMaxAttempts(), a.isGatesNext(), status, lockedReason, used, left, best,
                passed, inProgressId, availableEasy, availableMedium, availableHard);
    }

    public AttemptDto attempt(AttemptService.Detail detail) {
        Attempt attempt = detail.attempt();
        Assessment assessment = detail.assessment();
        boolean done = attempt.getStatus() == AttemptStatus.SUBMITTED;
        boolean practice = !assessment.isGraded();

        Long secondsLeft = attempt.getExpiresAt() == null || done ? null
                : Math.max(0, Duration.between(Instant.now(), attempt.getExpiresAt()).getSeconds());

        List<QuestionView> questions = new ArrayList<>();
        detail.items().stream().sorted(Comparator.comparingInt(AttemptQuestion::getPosition)).forEach(item -> {
            Question question = detail.questions().get(item.getQuestionId());
            if (question != null) {
                questions.add(questionView(item, question, done || (practice && item.getSelectedIndex() != null)));
            }
        });
        return new AttemptDto(attempt.getId(), assessment.getId(), assessment.getCourseId(), assessment.getModuleId(),
                assessment.getType().name(), assessment.getTitle(), attempt.getStatus().name(), attempt.getStartedAt(),
                attempt.getExpiresAt(), secondsLeft, attempt.isTimedOut(), attempt.getScorePercent(),
                attempt.getCorrectCount(), attempt.getTotalCount(), attempt.getPassed(),
                assessment.isGraded() ? assessment.getPassPercent() : null, questions);
    }

    private QuestionView questionView(AttemptQuestion item, Question question, boolean reveal) {
        List<String> options = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            options.add(question.getOptions().get(item.originalIndexOf(i)).getText());
        }
        Integer selected = item.getSelectedIndex() == null ? null : item.displayedIndexOf(item.getSelectedIndex());
        if (!reveal) {
            return new QuestionView(question.getId(), question.getText(), question.getDifficulty().name(), options,
                    selected, null, null, null, null, null);
        }
        return new QuestionView(question.getId(), question.getText(), question.getDifficulty().name(), options, selected,
                Boolean.TRUE.equals(item.getCorrect()), item.displayedIndexOf(AttemptService.correctIndex(question)),
                question.getExplanation(), question.getLessonId(), question.getSourceSeconds());
    }
}
