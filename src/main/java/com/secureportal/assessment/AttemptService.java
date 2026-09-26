package com.secureportal.assessment;

import com.secureportal.course.EnrollmentRequiredException;
import com.secureportal.course.LearningService;
import com.secureportal.quiz.Difficulty;
import com.secureportal.quiz.Question;
import com.secureportal.quiz.QuestionOption;
import com.secureportal.quiz.QuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Taking a quiz or assessment. The server owns everything that matters:
 * which questions are dealt, in what order, the clock, and the grade.
 * A learner is never told which option is correct until an attempt is submitted
 * (quizzes give instant feedback per answer, since they're practice).
 */
@Service
public class AttemptService {

    private static final Logger log = LoggerFactory.getLogger(AttemptService.class);

    /** Time allowed after the clock hits zero, for the auto-submit request to arrive. */
    static final Duration GRACE = Duration.ofSeconds(5);

    /** An attempt with everything needed to show it. */
    public record Detail(Attempt attempt, Assessment assessment, List<AttemptQuestion> items, Map<UUID, Question> questions) {
    }

    private final AssessmentService assessmentService;
    private final AssessmentAccess access;
    private final AttemptRepository attemptRepository;
    private final AttemptQuestionRepository itemRepository;
    private final QuestionRepository questionRepository;
    private final LearningService learningService;
    private final com.secureportal.gamification.GamificationService gamificationService;

    public AttemptService(AssessmentService assessmentService, AssessmentAccess access, AttemptRepository attemptRepository,
                          AttemptQuestionRepository itemRepository, QuestionRepository questionRepository,
                          LearningService learningService,
                          com.secureportal.gamification.GamificationService gamificationService) {
        this.assessmentService = assessmentService;
        this.access = access;
        this.attemptRepository = attemptRepository;
        this.itemRepository = itemRepository;
        this.questionRepository = questionRepository;
        this.learningService = learningService;
        this.gamificationService = gamificationService;
    }

    /** Starts an attempt, or returns the one already in progress. */
    public Detail start(UUID assessmentId, Long userId, boolean admin) {
        Assessment assessment = assessmentService.find(assessmentId);
        learningService.visibleCourse(assessment.getCourseId(), admin);
        if (!admin) {
            if (learningService.enrollment(userId, assessment.getCourseId()).isEmpty()) {
                throw new EnrollmentRequiredException();
            }
            access.lockReason(assessment, userId).ifPresent(reason -> {
                throw new AssessmentAccessException(reason);
            });
        }

        Attempt open = attemptRepository
                .findFirstByUserIdAndAssessmentIdAndStatus(userId, assessmentId, AttemptStatus.IN_PROGRESS).orElse(null);
        if (open != null) {
            finalizeIfExpired(open, assessment);
            if (open.getStatus() == AttemptStatus.IN_PROGRESS) {
                return detail(open, assessment);
            }
        }

        Integer limit = assessment.getMaxAttempts();
        if (assessment.isGraded() && limit != null && attemptRepository.countByUserIdAndAssessmentId(userId, assessmentId) >= limit) {
            throw new AssessmentAccessException("You have used all " + limit + " attempts.");
        }

        List<Question> drawn = draw(assessment);
        if (drawn.isEmpty()) {
            throw new AssessmentAccessException("There are no approved questions for this yet.");
        }
        Instant expires = assessment.isGraded() && assessment.getTimeLimitMinutes() != null
                ? Instant.now().plus(Duration.ofMinutes(assessment.getTimeLimitMinutes())) : null;
        Attempt attempt = attemptRepository.save(new Attempt(assessmentId, userId, drawn.size(), expires));

        List<AttemptQuestion> items = new ArrayList<>();
        for (int i = 0; i < drawn.size(); i++) {
            List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
            Collections.shuffle(order);
            items.add(new AttemptQuestion(attempt.getId(), i, drawn.get(i).getId(),
                    order.stream().map(String::valueOf).collect(Collectors.joining(","))));
        }
        itemRepository.saveAll(items);
        return detail(attempt, assessment);
    }

    public Detail load(UUID attemptId, Long userId, boolean admin) {
        Attempt attempt = find(attemptId, userId, admin);
        Assessment assessment = assessmentService.find(attempt.getAssessmentId());
        finalizeIfExpired(attempt, assessment);
        return detail(attempt, assessment);
    }

    public Detail saveAnswer(UUID attemptId, UUID questionId, int displayedIndex, Long userId) {
        Attempt attempt = find(attemptId, userId, false);
        Assessment assessment = assessmentService.find(attempt.getAssessmentId());
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AttemptClosedException("This attempt was already submitted.");
        }
        if (expired(attempt)) {
            finalizeIfExpired(attempt, assessment);
            throw new AttemptClosedException("Time's up — your answers so far were submitted.");
        }
        if (displayedIndex < 0 || displayedIndex > 3) {
            throw new InvalidAssessmentException("Choose one of the 4 options.");
        }

        List<AttemptQuestion> items = itemRepository.findByAttemptIdOrderByPositionAsc(attemptId);
        AttemptQuestion item = items.stream().filter(i -> i.getQuestionId().equals(questionId)).findFirst()
                .orElseThrow(() -> new InvalidAssessmentException("That question isn't part of this attempt."));
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new InvalidAssessmentException("That question no longer exists."));

        int original = item.originalIndexOf(displayedIndex);
        item.answer(original, original == correctIndex(question));
        itemRepository.save(item);
        return detail(attempt, assessment);
    }

    public Detail submit(UUID attemptId, Long userId) {
        Attempt attempt = find(attemptId, userId, false);
        Assessment assessment = assessmentService.find(attempt.getAssessmentId());
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            grade(attempt, assessment, expired(attempt));
        }
        return detail(attempt, assessment);
    }

    public List<Attempt> history(UUID assessmentId, Long userId) {
        Assessment assessment = assessmentService.find(assessmentId);
        List<Attempt> attempts = attemptRepository.findByUserIdAndAssessmentIdOrderByStartedAtDesc(userId, assessmentId);
        attempts.forEach(a -> finalizeIfExpired(a, assessment));
        return attempts;
    }

    /** Grades any of this learner's attempts whose time ran out while they were away. */
    public void finalizeExpired(Long userId, Map<UUID, Assessment> assessments) {
        if (assessments.isEmpty()) {
            return;
        }
        for (Attempt attempt : attemptRepository.findByUserIdAndAssessmentIdIn(userId, assessments.keySet())) {
            finalizeIfExpired(attempt, assessments.get(attempt.getAssessmentId()));
        }
    }

    // ---- internals ----

    private Attempt find(UUID attemptId, Long userId, boolean admin) {
        Attempt attempt = attemptRepository.findById(attemptId).orElseThrow(() -> new AttemptNotFoundException(attemptId));
        if (!admin && !attempt.getUserId().equals(userId)) {
            throw new AttemptNotFoundException(attemptId);
        }
        return attempt;
    }

    private boolean expired(Attempt attempt) {
        return attempt.getExpiresAt() != null && Instant.now().isAfter(attempt.getExpiresAt().plus(GRACE));
    }

    private void finalizeIfExpired(Attempt attempt, Assessment assessment) {
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS && expired(attempt)) {
            grade(attempt, assessment, true);
        }
    }

    private void grade(Attempt attempt, Assessment assessment, boolean timedOut) {
        List<AttemptQuestion> items = itemRepository.findByAttemptIdOrderByPositionAsc(attempt.getId());
        Map<UUID, Question> questions = questionRepository.findAllById(
                items.stream().map(AttemptQuestion::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        int correct = 0;
        for (AttemptQuestion item : items) {
            Question question = questions.get(item.getQuestionId());
            boolean right = question != null && item.getSelectedIndex() != null
                    && item.getSelectedIndex() == correctIndex(question);
            item.mark(right);
            if (right) {
                correct++;
            }
        }
        itemRepository.saveAll(items);

        int total = items.size();
        int score = total == 0 ? 0 : (int) Math.round(100.0 * correct / total);
        Boolean passed = assessment.isGraded() ? score >= assessment.getPassPercent() : null;
        attempt.grade(correct, total, score, passed, timedOut);
        attemptRepository.save(attempt);

        rewardPass(attempt, assessment, score, passed);
    }

    /** Points are a bonus on top of the result: a problem awarding them must never lose the attempt. */
    private void rewardPass(Attempt attempt, Assessment assessment, int score, Boolean passed) {
        try {
            gamificationService.recordQuizAttempt(attempt.getUserId(), assessment.getId().toString(), score,
                    Boolean.TRUE.equals(passed), null);
        } catch (RuntimeException e) {
            log.warn("Could not award points for attempt {} of user {}", attempt.getId(), attempt.getUserId(), e);
        }
    }

    private Detail detail(Attempt attempt, Assessment assessment) {
        List<AttemptQuestion> items = itemRepository.findByAttemptIdOrderByPositionAsc(attempt.getId());
        Map<UUID, Question> questions = questionRepository.findAllById(
                items.stream().map(AttemptQuestion::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));
        return new Detail(attempt, assessment, items, questions);
    }

    private List<Question> draw(Assessment assessment) {
        Map<Difficulty, List<Question>> regular = byDifficulty(assessmentService.pool(assessment));
        Map<Difficulty, List<Question>> fresh = byDifficulty(assessmentService.newPool(assessment));
        // A final assessment takes reusePercent of each difficulty from the regular questions and the rest from
        // the final-only ones; a shortfall in either is made up from the other so the count still adds up.
        int reuse = assessment.getModuleId() == null && assessment.getReusePercent() != null ? assessment.getReusePercent() : 100;

        Map<Difficulty, Integer> wanted = new EnumMap<>(Difficulty.class);
        wanted.put(Difficulty.EASY, assessment.getEasyCount());
        wanted.put(Difficulty.MEDIUM, assessment.getMediumCount());
        wanted.put(Difficulty.HARD, assessment.getHardCount());

        List<Question> drawn = new ArrayList<>();
        for (Difficulty difficulty : Difficulty.values()) {
            List<Question> old = regular.getOrDefault(difficulty, new ArrayList<>());
            List<Question> add = fresh.getOrDefault(difficulty, new ArrayList<>());
            Collections.shuffle(old);
            Collections.shuffle(add);
            int total = wanted.get(difficulty);
            int fromOld = Math.min(old.size(), (int) Math.round(total * reuse / 100.0));
            int fromNew = Math.min(add.size(), total - fromOld);
            fromOld = Math.min(old.size(), total - fromNew);
            drawn.addAll(old.subList(0, fromOld));
            drawn.addAll(add.subList(0, fromNew));
        }
        Collections.shuffle(drawn);
        return drawn;
    }

    private static Map<Difficulty, List<Question>> byDifficulty(List<Question> questions) {
        Map<Difficulty, List<Question>> map = new EnumMap<>(Difficulty.class);
        for (Question q : questions) {
            map.computeIfAbsent(q.getDifficulty(), d -> new ArrayList<>()).add(q);
        }
        return map;
    }

    public static int correctIndex(Question question) {
        List<QuestionOption> options = question.getOptions();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).isCorrect()) {
                return i;
            }
        }
        return -1;
    }
}
