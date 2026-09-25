package com.secureportal.quiz;

import com.secureportal.course.CourseService;
import com.secureportal.course.CourseStructureService;
import com.secureportal.course.Lesson;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Managing the question bank: listing, typing in, importing, editing, approving and deleting questions. */
@Service
public class QuestionService {

    static final int MAX_IMPORT_ROWS = 500;
    static final long MAX_IMPORT_BYTES = 1024 * 1024;
    private static final List<String> REQUIRED_COLUMNS =
            List.of("question", "difficulty", "option1", "option2", "option3", "option4", "correct");

    private final QuestionRepository questionRepository;
    private final CourseService courseService;
    private final CourseStructureService structureService;

    public QuestionService(QuestionRepository questionRepository, CourseService courseService,
                           CourseStructureService structureService) {
        this.questionRepository = questionRepository;
        this.courseService = courseService;
        this.structureService = structureService;
    }

    public List<Question> listForCourse(UUID courseId) {
        courseService.find(courseId);
        return questionRepository.findByCourseIdOrderByCreatedAtAsc(courseId);
    }

    public Question addManual(UUID lessonId, QuestionInput input) {
        Lesson lesson = structureService.findLesson(lessonId);
        return questionRepository.save(QuestionFactory.build(lesson.getCourseId(), lessonId, QuestionSource.MANUAL,
                QuestionStatus.APPROVED, input.text(), input.difficulty(), input.options(), input.correctIndex(),
                input.explanation(), null, false));
    }

    /**
     * Imports what it can and reports the rest row by row, so one typo doesn't cost the whole file.
     * Columns: question, difficulty, option1-4, correct (1-4 or A-D), and an optional explanation.
     */
    public ImportResult importCsv(UUID lessonId, MultipartFile file) {
        Lesson lesson = structureService.findLesson(lessonId);
        if (file == null || file.isEmpty()) {
            throw new InvalidQuestionException("Choose a CSV file to import.");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".csv")) {
            throw new InvalidQuestionException("The file must be a .csv file.");
        }
        if (file.getSize() > MAX_IMPORT_BYTES) {
            throw new InvalidQuestionException("The CSV must be 1 MB or smaller.");
        }

        List<CsvParser.Row> rows;
        try {
            rows = CsvParser.parse(new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new InvalidQuestionException("Could not read the uploaded file. Please try again.");
        }
        if (rows.isEmpty()) {
            throw new InvalidQuestionException("The CSV is empty.");
        }

        Map<String, Integer> columns = new HashMap<>();
        List<String> header = rows.get(0).cells();
        for (int i = 0; i < header.size(); i++) {
            columns.putIfAbsent(header.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        List<String> missing = REQUIRED_COLUMNS.stream().filter(c -> !columns.containsKey(c)).toList();
        if (!missing.isEmpty()) {
            throw new InvalidQuestionException("The CSV is missing these columns: " + String.join(", ", missing)
                    + ". The first row must be a header.");
        }
        if (rows.size() - 1 > MAX_IMPORT_ROWS) {
            throw new InvalidQuestionException("A CSV can hold at most " + MAX_IMPORT_ROWS + " questions.");
        }

        List<Question> valid = new ArrayList<>();
        List<ImportResult.RowError> errors = new ArrayList<>();
        for (CsvParser.Row row : rows.subList(1, rows.size())) {
            try {
                valid.add(QuestionFactory.build(lesson.getCourseId(), lessonId, QuestionSource.IMPORT,
                        QuestionStatus.APPROVED, cell(row, columns, "question"), cell(row, columns, "difficulty"),
                        List.of(cell(row, columns, "option1"), cell(row, columns, "option2"),
                                cell(row, columns, "option3"), cell(row, columns, "option4")),
                        correctIndex(cell(row, columns, "correct")),
                        columns.containsKey("explanation") ? cell(row, columns, "explanation") : null, null, false));
            } catch (InvalidQuestionException e) {
                errors.add(new ImportResult.RowError(row.line(), e.getMessage()));
            }
        }
        questionRepository.saveAll(valid);
        return new ImportResult(valid.size(), errors);
    }

    @Transactional
    public Question edit(UUID questionId, QuestionInput input) {
        Question question = find(questionId);
        int correct = input.correctIndex();
        QuestionFactory.Checked checked = QuestionFactory.check(input.text(), input.difficulty(), input.options(),
                correct, input.explanation(), false);
        question.edit(checked.text(), checked.difficulty(), checked.explanation(), checked.options());
        return question;
    }

    @Transactional
    public Question setStatus(UUID questionId, QuestionStatus status) {
        Question question = find(questionId);
        question.setStatus(status);
        return question;
    }

    @Transactional
    public void delete(UUID questionId) {
        questionRepository.delete(find(questionId));
    }

    public Question find(UUID questionId) {
        return questionRepository.findById(questionId).orElseThrow(() -> new QuestionNotFoundException(questionId));
    }

    private static String cell(CsvParser.Row row, Map<String, Integer> columns, String name) {
        int index = columns.get(name);
        return index < row.cells().size() ? row.cells().get(index) : "";
    }

    private static int correctIndex(String value) {
        String v = value.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "1", "A" -> 0;
            case "2", "B" -> 1;
            case "3", "C" -> 2;
            case "4", "D" -> 3;
            default -> throw new InvalidQuestionException("'correct' must be 1-4 or A-D.");
        };
    }
}
