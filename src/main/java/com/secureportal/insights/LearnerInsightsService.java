package com.secureportal.insights;

import com.secureportal.assessment.Assessment;
import com.secureportal.assessment.AssessmentRepository;
import com.secureportal.assessment.Attempt;
import com.secureportal.assessment.AttemptQuestionRepository;
import com.secureportal.assessment.AttemptRepository;
import com.secureportal.assessment.AttemptStatus;
import com.secureportal.course.Course;
import com.secureportal.course.CourseRepository;
import com.secureportal.course.Enrollment;
import com.secureportal.course.EnrollmentRepository;
import com.secureportal.course.LearningService;
import com.secureportal.course.LessonProgressRepository;
import com.secureportal.course.LessonRepository;
import com.secureportal.gamification.GamificationService;
import com.secureportal.interview.MockInterviewQuestionRepository;
import com.secureportal.interview.MockInterviewSession;
import com.secureportal.interview.MockInterviewSessionRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Pulls a learner's own numbers together from course progress, quizzes/assessments, mock interviews and
 *  gamification into one picture — where they stand, where they're strong, and specifically what to work on
 *  next. Nothing here writes anything; it only reads what those features already recorded. */
@Service
public class LearnerInsightsService {

    private static final List<String> DIFFICULTY_ORDER = List.of("EASY", "MEDIUM", "HARD");

    private final EnrollmentRepository enrollmentRepository;
    private final LessonProgressRepository progressRepository;
    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final AttemptRepository attemptRepository;
    private final AttemptQuestionRepository attemptQuestionRepository;
    private final AssessmentRepository assessmentRepository;
    private final MockInterviewSessionRepository interviewSessionRepository;
    private final MockInterviewQuestionRepository interviewQuestionRepository;
    private final GamificationService gamificationService;
    private final LearningService learningService;

    public LearnerInsightsService(EnrollmentRepository enrollmentRepository, LessonProgressRepository progressRepository,
                                  LessonRepository lessonRepository, CourseRepository courseRepository,
                                  AttemptRepository attemptRepository, AttemptQuestionRepository attemptQuestionRepository,
                                  AssessmentRepository assessmentRepository,
                                  MockInterviewSessionRepository interviewSessionRepository,
                                  MockInterviewQuestionRepository interviewQuestionRepository,
                                  GamificationService gamificationService, LearningService learningService) {
        this.enrollmentRepository = enrollmentRepository;
        this.progressRepository = progressRepository;
        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
        this.attemptRepository = attemptRepository;
        this.attemptQuestionRepository = attemptQuestionRepository;
        this.assessmentRepository = assessmentRepository;
        this.interviewSessionRepository = interviewSessionRepository;
        this.interviewQuestionRepository = interviewQuestionRepository;
        this.gamificationService = gamificationService;
        this.learningService = learningService;
    }

    public record CourseProgress(UUID courseId, String title, int progressPercent, int completedLessons,
                                 int totalLessons, boolean completed, Instant enrolledAt, Instant lastAccessedAt) {
    }

    public record CategoryScore(String category, double averageScore, int attemptCount) {
    }

    public record DifficultyScore(String difficulty, double accuracyPercent, int correct, int total) {
    }

    public record RecentAttempt(String assessmentTitle, String courseTitle, Integer scorePercent, Boolean passed, Instant submittedAt) {
    }

    /** {@code recentTrend} is the average of the newer half of attempts minus the older half — positive
     *  means improving, negative means slipping, null means not enough attempts yet to say. */
    public record QuizStats(int attemptCount, double averageScore, List<CategoryScore> byCategory,
                            List<DifficultyScore> byDifficulty, List<RecentAttempt> recent, Double recentTrend) {
    }

    public record RecentInterview(String targetRole, int overallScore, String readinessLevel, Instant completedAt) {
    }

    public record QuestionCategoryScore(String category, double averageScore, int questionCount) {
    }

    /** {@code strongest}/{@code weakest} name one of "Relevance", "Depth", "Structure" or "Communication" —
     *  null until at least one interview has been scored. {@code latestTopFix} is the AI's own note on the
     *  single weakest answer from the most recent completed interview — the most literal, specific "what to
     *  improve" this app already generates, just never surfaced anywhere until now. */
    public record InterviewStats(int sessionsCompleted, double averageOverallScore, Double avgRelevance, Double avgDepth,
                                 Double avgStructure, Double avgCommunication, String strongest, String weakest,
                                 List<QuestionCategoryScore> byQuestionCategory, String latestTopFix, String latestSummary,
                                 List<RecentInterview> recent, Double recentTrend) {
    }

    public record Insights(int overallCompletionPercent, int coursesEnrolled, int coursesCompleted,
                           List<CourseProgress> courses, long totalWatchSeconds, QuizStats quizzes,
                           InterviewStats interviews, GamificationService.Summary gamification) {
    }

    @Transactional(readOnly = true)
    public Insights forUser(Long userId) {
        List<Enrollment> myEnrollments = enrollmentRepository.findByUserId(userId);
        List<UUID> courseIds = myEnrollments.stream().map(Enrollment::getCourseId).toList();
        Map<UUID, Course> courseById = courseRepository.findAllById(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, c -> c));

        List<CourseProgress> courseProgress = new ArrayList<>();
        int coursesCompleted = 0;
        for (Enrollment e : myEnrollments) {
            Course course = courseById.get(e.getCourseId());
            if (course == null) {
                continue; // deleted since enrolling
            }
            long total = lessonRepository.countByCourseId(course.getId());
            long done = progressRepository.countByUserIdAndCourseIdAndCompletedTrue(userId, course.getId());
            int percent = LearningService.percent(done, total);
            boolean isDone = percent >= 100;
            if (isDone) {
                coursesCompleted++;
            }
            courseProgress.add(new CourseProgress(course.getId(), course.getTitle(), percent, (int) done, (int) total,
                    isDone, e.getEnrolledAt(), e.getLastAccessedAt()));
        }
        courseProgress.sort(Comparator.comparing(CourseProgress::enrolledAt).reversed());
        int overallCompletion = courseProgress.isEmpty() ? 0
                : (int) Math.round(courseProgress.stream().mapToInt(CourseProgress::progressPercent).average().orElse(0));

        long watchSeconds = progressRepository.totalWatchedSeconds(userId);

        return new Insights(overallCompletion, myEnrollments.size(), coursesCompleted, courseProgress, watchSeconds,
                quizStats(userId, courseById), interviewStats(userId), gamificationService.summary(userId));
    }

    private QuizStats quizStats(Long userId, Map<UUID, Course> courseById) {
        List<Attempt> myAttempts = attemptRepository.findByUserId(userId).stream()
                .filter(a -> a.getStatus() == AttemptStatus.SUBMITTED && a.getScorePercent() != null)
                .sorted(Comparator.comparing(Attempt::getSubmittedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        Set<UUID> assessmentIds = myAttempts.stream().map(Attempt::getAssessmentId).collect(Collectors.toSet());
        Map<UUID, Assessment> assessmentById = assessmentRepository.findAllById(assessmentIds).stream()
                .collect(Collectors.toMap(Assessment::getId, a -> a));

        Map<String, List<Integer>> byCategory = new LinkedHashMap<>();
        List<RecentAttempt> recent = new ArrayList<>();
        for (Attempt attempt : myAttempts) {
            Assessment assessment = assessmentById.get(attempt.getAssessmentId());
            Course course = assessment == null ? null : courseById.get(assessment.getCourseId());
            String category = course == null || course.getCategory() == null || course.getCategory().isBlank()
                    ? "Uncategorised" : course.getCategory();
            byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(attempt.getScorePercent());
            if (recent.size() < 10) {
                recent.add(new RecentAttempt(assessment == null ? "Assessment" : assessment.getTitle(),
                        course == null ? null : course.getTitle(), attempt.getScorePercent(), attempt.getPassed(),
                        attempt.getSubmittedAt()));
            }
        }
        List<CategoryScore> categoryScores = byCategory.entrySet().stream()
                .map(entry -> new CategoryScore(entry.getKey(), average(entry.getValue()), entry.getValue().size()))
                .sorted(Comparator.comparingDouble(CategoryScore::averageScore))
                .toList();

        List<DifficultyScore> difficultyScores = difficultyBreakdown(userId);

        double overallAverage = average(myAttempts.stream().map(Attempt::getScorePercent).toList());
        Double trend = trendOf(myAttempts.stream().map(a -> (double) a.getScorePercent()).toList());
        return new QuizStats(myAttempts.size(), overallAverage, categoryScores, difficultyScores, recent, trend);
    }

    private List<DifficultyScore> difficultyBreakdown(Long userId) {
        List<Object[]> rows = attemptQuestionRepository.difficultyResultsForUser(userId, AttemptStatus.SUBMITTED);
        Map<String, int[]> tally = new LinkedHashMap<>(); // [correct, total]
        for (Object[] row : rows) {
            String difficulty = String.valueOf(row[0]);
            boolean correct = Boolean.TRUE.equals(row[1]);
            int[] counts = tally.computeIfAbsent(difficulty, k -> new int[2]);
            if (correct) counts[0]++;
            counts[1]++;
        }
        return DIFFICULTY_ORDER.stream()
                .filter(tally::containsKey)
                .map(d -> {
                    int[] counts = tally.get(d);
                    double accuracy = counts[1] == 0 ? 0 : 100.0 * counts[0] / counts[1];
                    return new DifficultyScore(d, accuracy, counts[0], counts[1]);
                })
                .toList();
    }

    private InterviewStats interviewStats(Long userId) {
        List<MockInterviewSession> completed = interviewSessionRepository
                .findByUserIdOrderByCreatedAtDescIdDesc(userId, Pageable.unpaged()).stream()
                .filter(s -> "COMPLETED".equals(s.getStatus()))
                .toList();

        List<Object[]> rubricRows = interviewQuestionRepository.averageRubricForUser(userId);
        Object[] rubric = rubricRows.isEmpty() ? new Object[4] : rubricRows.get(0);
        Double avgRelevance = (Double) rubric[0];
        Double avgDepth = (Double) rubric[1];
        Double avgStructure = (Double) rubric[2];
        Double avgCommunication = (Double) rubric[3];

        Map<String, Double> byCompetency = new LinkedHashMap<>();
        if (avgRelevance != null) byCompetency.put("Relevance", avgRelevance);
        if (avgDepth != null) byCompetency.put("Depth", avgDepth);
        if (avgStructure != null) byCompetency.put("Structure", avgStructure);
        if (avgCommunication != null) byCompetency.put("Communication", avgCommunication);
        String strongest = byCompetency.isEmpty() ? null
                : java.util.Collections.max(byCompetency.entrySet(), Map.Entry.comparingByValue()).getKey();
        String weakest = byCompetency.isEmpty() ? null
                : java.util.Collections.min(byCompetency.entrySet(), Map.Entry.comparingByValue()).getKey();

        List<QuestionCategoryScore> byQuestionCategory = interviewQuestionRepository.averageScoreByCategoryForUser(userId)
                .stream()
                .map(row -> new QuestionCategoryScore(labelForCategory(String.valueOf(row[0])),
                        (Double) row[1], ((Number) row[2]).intValue()))
                .sorted(Comparator.comparingDouble(QuestionCategoryScore::averageScore))
                .toList();

        List<RecentInterview> recent = completed.stream().limit(10)
                .map(s -> new RecentInterview(s.getTargetRole(), s.getOverallScore(), s.getReadinessLevel(), s.getCompletedAt()))
                .toList();
        double averageOverall = completed.isEmpty() ? 0
                : completed.stream().mapToInt(MockInterviewSession::getOverallScore).average().orElse(0);
        Double trend = trendOf(completed.stream().map(s -> (double) s.getOverallScore()).toList());

        MockInterviewSession latest = completed.isEmpty() ? null : completed.get(0);

        return new InterviewStats(completed.size(), averageOverall, avgRelevance, avgDepth, avgStructure,
                avgCommunication, strongest, weakest, byQuestionCategory,
                latest == null ? null : latest.getTopFix(), latest == null ? null : latest.getSummaryFeedback(),
                recent, trend);
    }

    private static String labelForCategory(String raw) {
        return switch (raw) {
            case "SYSTEM_DESIGN" -> "System design";
            case "PROBLEM_SOLVING" -> "Problem solving";
            case "BEHAVIORAL" -> "Behavioural";
            case "TECHNICAL" -> "Technical";
            default -> raw;
        };
    }

    /** The newer half's average minus the older half's — positive means improving. Needs at least 3 data
     *  points (chronologically newest first) to say anything; fewer than that is too noisy to call a trend. */
    private static Double trendOf(List<Double> newestFirst) {
        if (newestFirst.size() < 3) {
            return null;
        }
        int recentCount = Math.max(1, newestFirst.size() / 2);
        List<Double> recent = newestFirst.subList(0, recentCount);
        List<Double> earlier = newestFirst.subList(recentCount, newestFirst.size());
        if (earlier.isEmpty()) {
            return null;
        }
        return averageD(recent) - averageD(earlier);
    }

    private static double average(List<Integer> values) {
        return values.isEmpty() ? 0 : values.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    private static double averageD(List<Double> values) {
        return values.isEmpty() ? 0 : values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
}
