package com.secureportal.api;

import com.secureportal.api.dto.QuestionDto;
import com.secureportal.audit.AuditService;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.CourseStructureService;
import com.secureportal.course.Lesson;
import com.secureportal.quiz.Difficulty;
import com.secureportal.quiz.ImportResult;
import com.secureportal.quiz.Question;
import com.secureportal.quiz.QuestionGenerationService;
import com.secureportal.quiz.QuestionInput;
import com.secureportal.quiz.QuestionService;
import com.secureportal.quiz.QuestionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The question bank: generate with AI, type in or import questions, then review, edit and approve them. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminQuestionApiController {

    private static final int DEFAULT_COUNT = 10;

    private final QuestionService questionService;
    private final QuestionGenerationService generationService;
    private final CourseStructureService structureService;
    private final AuditService auditService;

    public AdminQuestionApiController(QuestionService questionService, QuestionGenerationService generationService,
                                      CourseStructureService structureService, AuditService auditService) {
        this.questionService = questionService;
        this.generationService = generationService;
        this.structureService = structureService;
        this.auditService = auditService;
    }

    public record GenerateRequest(@Min(1) @Max(30) Integer count) {
    }

    public record QuestionRequest(
            @NotBlank(message = "The question text is required") @Size(max = 1000, message = "The question text must be 1000 characters or fewer") String text,
            @NotNull(message = "Choose a difficulty") Difficulty difficulty,
            @Size(max = 1000, message = "The explanation must be 1000 characters or fewer") String explanation,
            @NotNull(message = "A question needs exactly 4 options") @Size(min = 4, max = 4, message = "A question needs exactly 4 options")
            List<@NotBlank(message = "Options can't be empty") @Size(max = 500, message = "Each option must be 500 characters or fewer") String> options,
            @Min(value = 0, message = "Choose the correct option") @Max(value = 3, message = "Choose the correct option") int correctIndex
    ) {
        QuestionInput toInput() {
            return new QuestionInput(text, difficulty.name(), options, correctIndex, explanation);
        }
    }

    @GetMapping("/courses/{courseId}/questions")
    public List<QuestionDto> list(@PathVariable UUID courseId) {
        List<Question> questions = questionService.listForCourse(courseId);
        Map<UUID, String> titles = new HashMap<>();
        structureService.orderedLessons(courseId).forEach(l -> titles.put(l.getId(), l.getTitle()));
        return questions.stream().map(q -> QuestionDto.from(q, titles.get(q.getLessonId()))).toList();
    }

    @PostMapping("/lessons/{lessonId}/questions/generate")
    public List<QuestionDto> generate(@PathVariable UUID lessonId,
                                      @Valid @RequestBody(required = false) GenerateRequest request,
                                      @AuthenticationPrincipal AppPrincipal principal) {
        Lesson lesson = structureService.findLesson(lessonId);
        int count = request != null && request.count() != null ? request.count() : DEFAULT_COUNT;
        List<QuestionDto> created = generationService.generate(lessonId, count).stream()
                .map(q -> QuestionDto.from(q, lesson.getTitle())).toList();
        auditService.log(principal.getEmail(), "QUESTIONS_GENERATE", lesson.getCourseId(),
                created.size() + " draft questions for \"" + lesson.getTitle() + "\"");
        return created;
    }

    @PostMapping("/lessons/{lessonId}/questions")
    public QuestionDto add(@PathVariable UUID lessonId, @Valid @RequestBody QuestionRequest request,
                           @AuthenticationPrincipal AppPrincipal principal) {
        Lesson lesson = structureService.findLesson(lessonId);
        Question created = questionService.addManual(lessonId, request.toInput());
        auditService.log(principal.getEmail(), "QUESTION_ADD", lesson.getCourseId(), "for \"" + lesson.getTitle() + "\"");
        return QuestionDto.from(created, lesson.getTitle());
    }

    @PostMapping("/lessons/{lessonId}/questions/import")
    public ImportResult importCsv(@PathVariable UUID lessonId, @RequestParam("file") MultipartFile file,
                                  @AuthenticationPrincipal AppPrincipal principal) {
        Lesson lesson = structureService.findLesson(lessonId);
        ImportResult result = questionService.importCsv(lessonId, file);
        auditService.log(principal.getEmail(), "QUESTIONS_IMPORT", lesson.getCourseId(),
                result.imported() + " questions for \"" + lesson.getTitle() + "\"");
        return result;
    }

    @PutMapping("/questions/{questionId}")
    public QuestionDto edit(@PathVariable UUID questionId, @Valid @RequestBody QuestionRequest request,
                            @AuthenticationPrincipal AppPrincipal principal) {
        return audited(questionService.edit(questionId, request.toInput()), "QUESTION_EDIT", principal);
    }

    @PostMapping("/questions/{questionId}/approve")
    public QuestionDto approve(@PathVariable UUID questionId, @AuthenticationPrincipal AppPrincipal principal) {
        return audited(questionService.setStatus(questionId, QuestionStatus.APPROVED), "QUESTION_APPROVE", principal);
    }

    @PostMapping("/questions/{questionId}/unapprove")
    public QuestionDto unapprove(@PathVariable UUID questionId, @AuthenticationPrincipal AppPrincipal principal) {
        return audited(questionService.setStatus(questionId, QuestionStatus.DRAFT), "QUESTION_UNAPPROVE", principal);
    }

    @DeleteMapping("/questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID questionId, @AuthenticationPrincipal AppPrincipal principal) {
        Question question = questionService.find(questionId);
        questionService.delete(questionId);
        auditService.log(principal.getEmail(), "QUESTION_DELETE", question.getCourseId(), "question " + questionId);
    }

    private QuestionDto audited(Question question, String action, AppPrincipal principal) {
        Lesson lesson = structureService.findLesson(question.getLessonId());
        auditService.log(principal.getEmail(), action, question.getCourseId(), "question " + question.getId());
        return QuestionDto.from(question, lesson.getTitle());
    }
}
