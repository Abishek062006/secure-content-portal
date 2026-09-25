package com.secureportal.assessment;

import com.secureportal.course.CourseService;
import com.secureportal.course.CourseStructureService;
import com.secureportal.course.Lesson;
import com.secureportal.course.LessonRepository;
import com.secureportal.quiz.Difficulty;
import com.secureportal.quiz.Question;
import com.secureportal.quiz.QuestionRepository;
import com.secureportal.quiz.QuestionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Admin-side setup: which modules end in a quiz or an assessment, and how each one is drawn and graded. */
@Service
public class AssessmentService {

    static final int MAX_PER_DIFFICULTY = 50;

    private final AssessmentRepository assessmentRepository;
    private final CourseService courseService;
    private final CourseStructureService structureService;
    private final LessonRepository lessonRepository;
    private final QuestionRepository questionRepository;

    public AssessmentService(AssessmentRepository assessmentRepository, CourseService courseService,
                             CourseStructureService structureService, LessonRepository lessonRepository,
                             QuestionRepository questionRepository) {
        this.assessmentRepository = assessmentRepository;
        this.courseService = courseService;
        this.structureService = structureService;
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
    }

    public List<Assessment> forCourse(UUID courseId) {
        return assessmentRepository.findByCourseId(courseId);
    }

    public Assessment find(UUID id) {
        return assessmentRepository.findById(id).orElseThrow(() -> new AssessmentNotFoundException(id));
    }

    @Transactional
    public Assessment saveForModule(UUID moduleId, AssessmentInput input) {
        var module = structureService.findModule(moduleId);
        AssessmentInput checked = validate(input, true);
        return assessmentRepository.findByModuleId(moduleId)
                .map(existing -> {
                    existing.apply(checked);
                    return existing;
                })
                .orElseGet(() -> assessmentRepository.save(new Assessment(module.getCourseId(), moduleId, checked)));
    }

    @Transactional
    public Assessment saveFinal(UUID courseId, AssessmentInput input) {
        courseService.find(courseId);
        AssessmentInput checked = validate(input, false);
        return assessmentRepository.findByCourseIdAndModuleIdIsNull(courseId)
                .map(existing -> {
                    existing.apply(checked);
                    return existing;
                })
                .orElseGet(() -> assessmentRepository.save(new Assessment(courseId, null, checked)));
    }

    public void deleteForModule(UUID moduleId) {
        structureService.findModule(moduleId);
        assessmentRepository.findByModuleId(moduleId).ifPresent(assessmentRepository::delete);
    }

    public void deleteFinal(UUID courseId) {
        courseService.find(courseId);
        assessmentRepository.findByCourseIdAndModuleIdIsNull(courseId).ifPresent(assessmentRepository::delete);
    }

    /** Approved questions in this assessment's scope, by difficulty — what an attempt can be drawn from. */
    public Map<Difficulty, Long> available(Assessment assessment) {
        Map<Difficulty, Long> counts = new EnumMap<>(Difficulty.class);
        for (Difficulty d : Difficulty.values()) {
            counts.put(d, 0L);
        }
        for (Question q : pool(assessment)) {
            counts.merge(q.getDifficulty(), 1L, Long::sum);
        }
        return counts;
    }

    public List<Question> pool(Assessment assessment) {
        List<UUID> lessonIds = (assessment.getModuleId() != null
                ? lessonRepository.findByModuleIdOrderByPositionAsc(assessment.getModuleId())
                : lessonRepository.findByCourseId(assessment.getCourseId()))
                .stream().map(Lesson::getId).toList();
        return lessonIds.isEmpty() ? List.of() : questionRepository.findByLessonIdInAndStatus(lessonIds, QuestionStatus.APPROVED);
    }

    /** Normalises the input: a quiz never carries a pass mark, timer, attempt limit or gate. */
    AssessmentInput validate(AssessmentInput in, boolean moduleScope) {
        if (in.type() == null) {
            throw new InvalidAssessmentException("Choose quiz or assessment.");
        }
        String title = in.title() == null ? "" : in.title().trim();
        if (title.isEmpty() || title.length() > 200) {
            throw new InvalidAssessmentException("Give it a title (up to 200 characters).");
        }
        for (int count : new int[]{in.easyCount(), in.mediumCount(), in.hardCount()}) {
            if (count < 0 || count > MAX_PER_DIFFICULTY) {
                throw new InvalidAssessmentException("Each difficulty can draw 0 to " + MAX_PER_DIFFICULTY + " questions.");
            }
        }
        if (in.easyCount() + in.mediumCount() + in.hardCount() == 0) {
            throw new InvalidAssessmentException("Draw at least one question.");
        }
        if (in.type() == AssessmentType.QUIZ) {
            return new AssessmentInput(AssessmentType.QUIZ, title, in.easyCount(), in.mediumCount(), in.hardCount(),
                    null, null, null, false);
        }
        if (in.passPercent() == null || in.passPercent() < 1 || in.passPercent() > 100) {
            throw new InvalidAssessmentException("An assessment needs a pass mark between 1 and 100%.");
        }
        if (in.timeLimitMinutes() != null && (in.timeLimitMinutes() < 1 || in.timeLimitMinutes() > 300)) {
            throw new InvalidAssessmentException("The time limit must be 1 to 300 minutes.");
        }
        if (in.maxAttempts() != null && (in.maxAttempts() < 1 || in.maxAttempts() > 20)) {
            throw new InvalidAssessmentException("Attempts must be 1 to 20.");
        }
        return new AssessmentInput(AssessmentType.ASSESSMENT, title, in.easyCount(), in.mediumCount(), in.hardCount(),
                in.passPercent(), in.timeLimitMinutes(), in.maxAttempts(), moduleScope && in.gatesNext());
    }
}
