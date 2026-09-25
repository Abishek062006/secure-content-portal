package com.secureportal.course;

import com.secureportal.common.FileValidator;
import com.secureportal.common.ValidatedFile;
import com.secureportal.content.ContentType;
import com.secureportal.course.dto.LessonUploadForm;
import com.secureportal.course.dto.TranscriptCue;
import com.secureportal.storage.StorageObject;
import com.secureportal.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Modules and lessons: creating, editing, deleting and ordering them. */
@Service
public class CourseStructureService {

    private final CourseService courseService;
    private final CourseModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final StorageService storageService;
    private final FileValidator fileValidator;
    private final CourseFileStore fileStore;

    public CourseStructureService(CourseService courseService, CourseModuleRepository moduleRepository,
                                  LessonRepository lessonRepository, StorageService storageService,
                                  FileValidator fileValidator, CourseFileStore fileStore) {
        this.courseService = courseService;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.storageService = storageService;
        this.fileValidator = fileValidator;
        this.fileStore = fileStore;
    }

    // ---- modules ----

    public CourseModule addModule(UUID courseId, String title, String description) {
        courseService.find(courseId);
        int position = (int) moduleRepository.countByCourseId(courseId);
        return moduleRepository.save(new CourseModule(courseId, title, description, position));
    }

    @Transactional
    public CourseModule editModule(UUID moduleId, String title, String description) {
        CourseModule module = findModule(moduleId);
        module.edit(title, description);
        return module;
    }

    /** Lessons go with their module (the database cascades); their files are removed afterwards. */
    public void deleteModule(UUID moduleId) {
        CourseModule module = findModule(moduleId);
        List<String> keys = new ArrayList<>();
        for (Lesson lesson : lessonRepository.findByModuleIdOrderByPositionAsc(moduleId)) {
            keys.add(lesson.getVideoKey());
            keys.add(lesson.getTranscriptKey());
        }
        moduleRepository.delete(module);
        renumberModules(module.getCourseId());
        keys.forEach(fileStore::deleteQuietly);
    }

    @Transactional
    public void reorderModules(UUID courseId, List<UUID> orderedIds) {
        courseService.find(courseId);
        List<CourseModule> modules = moduleRepository.findByCourseIdOrderByPositionAsc(courseId);
        Map<UUID, CourseModule> byId = modules.stream().collect(Collectors.toMap(CourseModule::getId, Function.identity()));
        requireExactly(orderedIds, byId.keySet(), "module");
        for (int i = 0; i < orderedIds.size(); i++) {
            byId.get(orderedIds.get(i)).setPosition(i);
        }
    }

    // ---- lessons ----

    /**
     * Validates every file before storing any, and removes whatever was
     * already stored if a later step fails, so a rejected upload leaves nothing behind.
     */
    public Lesson addLesson(UUID moduleId, LessonUploadForm form) {
        CourseModule module = findModule(moduleId);
        ValidatedFile video = fileValidator.validate(form.getVideo(), ContentType.VIDEO);
        ValidatedFile transcript = present(form.getTranscript())
                ? fileValidator.validateTranscript(form.getTranscript()) : null;

        String base = "courses/" + module.getCourseId() + "/lessons/" + UUID.randomUUID();
        List<String> stored = new ArrayList<>();
        try {
            String videoKey = fileStore.put(stored, base + "/video/", form.getVideo(), video);
            Lesson lesson = new Lesson(moduleId, module.getCourseId(), form.getTitle(), form.getDescription(),
                    (int) lessonRepository.countByModuleId(moduleId), videoKey, video.originalFilename(),
                    video.detectedMimeType(), video.sizeBytes());
            if (transcript != null) {
                lesson.setTranscript(fileStore.put(stored, base + "/transcript/", form.getTranscript(), transcript),
                        transcript.originalFilename());
            }
            return lessonRepository.save(lesson);
        } catch (RuntimeException e) {
            stored.forEach(fileStore::deleteQuietly);
            throw e;
        }
    }

    @Transactional
    public Lesson editLesson(UUID lessonId, String title, String description) {
        Lesson lesson = findLesson(lessonId);
        lesson.edit(title, description);
        return lesson;
    }

    public Lesson replaceTranscript(UUID lessonId, MultipartFile file) {
        Lesson lesson = findLesson(lessonId);
        ValidatedFile validated = fileValidator.validateTranscript(file);
        String previous = lesson.getTranscriptKey();

        List<String> stored = new ArrayList<>();
        try {
            String key = fileStore.put(stored, "courses/" + lesson.getCourseId() + "/lessons/" + UUID.randomUUID()
                    + "/transcript/", file, validated);
            lesson.setTranscript(key, validated.originalFilename());
            Lesson saved = lessonRepository.save(lesson);
            fileStore.deleteQuietly(previous);
            return saved;
        } catch (RuntimeException e) {
            stored.forEach(fileStore::deleteQuietly);
            throw e;
        }
    }

    public void deleteLesson(UUID lessonId) {
        Lesson lesson = findLesson(lessonId);
        lessonRepository.delete(lesson);
        renumberLessons(lesson.getModuleId());
        fileStore.deleteQuietly(lesson.getVideoKey());
        fileStore.deleteQuietly(lesson.getTranscriptKey());
    }

    @Transactional
    public void reorderLessons(UUID moduleId, List<UUID> orderedIds) {
        findModule(moduleId);
        List<Lesson> lessons = lessonRepository.findByModuleIdOrderByPositionAsc(moduleId);
        Map<UUID, Lesson> byId = lessons.stream().collect(Collectors.toMap(Lesson::getId, Function.identity()));
        requireExactly(orderedIds, byId.keySet(), "lesson");
        for (int i = 0; i < orderedIds.size(); i++) {
            byId.get(orderedIds.get(i)).setPosition(i);
        }
    }

    // ---- reading ----

    public List<CourseModule> modules(UUID courseId) {
        return moduleRepository.findByCourseIdOrderByPositionAsc(courseId);
    }

    /** Every lesson of the course in learning order: by module position, then lesson position. */
    public List<Lesson> orderedLessons(UUID courseId) {
        Map<UUID, Integer> modulePosition = new java.util.HashMap<>();
        modules(courseId).forEach(m -> modulePosition.put(m.getId(), m.getPosition()));
        return lessonRepository.findByCourseId(courseId).stream()
                .sorted(java.util.Comparator
                        .comparingInt((Lesson l) -> modulePosition.getOrDefault(l.getModuleId(), Integer.MAX_VALUE))
                        .thenComparingInt(Lesson::getPosition))
                .toList();
    }

    public List<TranscriptCue> transcript(Lesson lesson) {
        if (lesson.getTranscriptKey() == null) {
            return List.of();
        }
        try (StorageObject object = storageService.get(lesson.getTranscriptKey(), null, null)) {
            return VttParser.parse(new String(object.content().readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read transcript for lesson " + lesson.getId(), e);
        }
    }

    public CourseModule findModule(UUID id) {
        return moduleRepository.findById(id).orElseThrow(() -> new ModuleNotFoundException(id));
    }

    public Lesson findLesson(UUID id) {
        return lessonRepository.findById(id).orElseThrow(() -> new LessonNotFoundException(id));
    }

    // ---- helpers ----

    private void renumberModules(UUID courseId) {
        List<CourseModule> modules = moduleRepository.findByCourseIdOrderByPositionAsc(courseId);
        for (int i = 0; i < modules.size(); i++) {
            modules.get(i).setPosition(i);
        }
        moduleRepository.saveAll(modules);
    }

    private void renumberLessons(UUID moduleId) {
        List<Lesson> lessons = lessonRepository.findByModuleIdOrderByPositionAsc(moduleId);
        for (int i = 0; i < lessons.size(); i++) {
            lessons.get(i).setPosition(i);
        }
        lessonRepository.saveAll(lessons);
    }

    private void requireExactly(List<UUID> ordered, java.util.Set<UUID> expected, String noun) {
        if (ordered == null || ordered.size() != expected.size() || !new HashSet<>(ordered).equals(expected)) {
            throw new CourseStructureException("The order must list every " + noun + " exactly once.");
        }
    }

    private boolean present(MultipartFile file) {
        return file != null && !file.isEmpty();
    }
}
