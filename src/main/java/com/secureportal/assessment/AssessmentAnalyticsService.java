package com.secureportal.assessment;

import com.secureportal.course.CourseModule;
import com.secureportal.course.CourseStructureService;
import com.secureportal.course.Lesson;
import com.secureportal.quiz.Question;
import com.secureportal.quiz.QuestionRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** How learners are doing: per-assessment results and the questions they get wrong most. */
@Service
public class AssessmentAnalyticsService {

    static final int HARDEST_LIMIT = 10;

    public record AssessmentStats(Assessment assessment, String moduleTitle, int attempts, int learners,
                                  Double averageScore, Double passRate) {
    }

    public record QuestionStats(Question question, String lessonTitle, int answered, int correct) {
        public double correctRate() {
            return answered == 0 ? 0 : (double) correct / answered;
        }
    }

    public record Results(List<AssessmentStats> assessments, List<QuestionStats> hardestQuestions) {
    }

    private final AssessmentRepository assessmentRepository;
    private final AttemptRepository attemptRepository;
    private final AttemptQuestionRepository itemRepository;
    private final QuestionRepository questionRepository;
    private final CourseStructureService structureService;

    public AssessmentAnalyticsService(AssessmentRepository assessmentRepository, AttemptRepository attemptRepository,
                                      AttemptQuestionRepository itemRepository, QuestionRepository questionRepository,
                                      CourseStructureService structureService) {
        this.assessmentRepository = assessmentRepository;
        this.attemptRepository = attemptRepository;
        this.itemRepository = itemRepository;
        this.questionRepository = questionRepository;
        this.structureService = structureService;
    }

    public Results forCourse(UUID courseId) {
        List<Assessment> assessments = assessmentRepository.findByCourseId(courseId);
        Map<UUID, String> moduleTitles = structureService.modules(courseId).stream()
                .collect(Collectors.toMap(CourseModule::getId, CourseModule::getTitle));
        Map<UUID, String> lessonTitles = structureService.orderedLessons(courseId).stream()
                .collect(Collectors.toMap(Lesson::getId, Lesson::getTitle));

        List<Attempt> attempts = assessments.isEmpty() ? List.of()
                : attemptRepository.findByAssessmentIdInAndStatus(
                        assessments.stream().map(Assessment::getId).toList(), AttemptStatus.SUBMITTED);
        Map<UUID, List<Attempt>> byAssessment = attempts.stream().collect(Collectors.groupingBy(Attempt::getAssessmentId));

        List<AssessmentStats> stats = assessments.stream().map(a -> {
            List<Attempt> mine = byAssessment.getOrDefault(a.getId(), List.of());
            Double average = mine.isEmpty() ? null
                    : mine.stream().mapToInt(x -> x.getScorePercent() == null ? 0 : x.getScorePercent()).average().orElse(0);
            Double passRate = !a.isGraded() || mine.isEmpty() ? null
                    : (double) mine.stream().filter(x -> Boolean.TRUE.equals(x.getPassed())).count() / mine.size();
            return new AssessmentStats(a, a.getModuleId() == null ? "Final assessment" : moduleTitles.get(a.getModuleId()),
                    mine.size(), new HashSet<>(mine.stream().map(Attempt::getUserId).toList()).size(), average, passRate);
        }).toList();

        Map<UUID, int[]> perQuestion = new HashMap<>();
        if (!attempts.isEmpty()) {
            itemRepository.findByAttemptIdIn(attempts.stream().map(Attempt::getId).toList()).stream()
                    .filter(i -> i.getCorrect() != null)
                    .forEach(i -> {
                        int[] counts = perQuestion.computeIfAbsent(i.getQuestionId(), k -> new int[2]);
                        counts[0]++;
                        if (i.getCorrect()) {
                            counts[1]++;
                        }
                    });
        }
        Map<UUID, Question> questions = questionRepository.findAllById(perQuestion.keySet()).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));
        List<QuestionStats> hardest = perQuestion.entrySet().stream()
                .filter(e -> questions.containsKey(e.getKey()))
                .map(e -> new QuestionStats(questions.get(e.getKey()), lessonTitles.get(questions.get(e.getKey()).getLessonId()),
                        e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingDouble(QuestionStats::correctRate).thenComparing(QuestionStats::answered, Comparator.reverseOrder()))
                .limit(HARDEST_LIMIT)
                .toList();
        return new Results(stats, hardest);
    }
}
