package com.secureportal.course;

import com.secureportal.common.FileValidator;
import com.secureportal.common.ValidatedFile;
import com.secureportal.content.dto.EditForm;
import com.secureportal.course.dto.CourseCreateForm;
import com.secureportal.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A course's own details: title, cover image, publish state. Its modules and lessons live in {@link CourseStructureService}. */
@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final FileValidator fileValidator;
    private final CourseMaterialRepository materialRepository;
    private final CourseFileStore fileStore;

    public CourseService(CourseRepository courseRepository, LessonRepository lessonRepository,
                         FileValidator fileValidator, CourseFileStore fileStore,
                         CourseMaterialRepository materialRepository) {
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.fileValidator = fileValidator;
        this.materialRepository = materialRepository;
        this.fileStore = fileStore;
    }

    public Course create(CourseCreateForm form, User createdBy) {
        ValidatedFile thumbnail = present(form.getThumbnail())
                ? fileValidator.validateThumbnail(form.getThumbnail()) : null;

        Course course = new Course(form.getTitle(), form.getDescription(), form.getCategory(), createdBy);
        course.setPricing(CoursePricing.check(form.getPriceRupees(), form.getDiscountPercent(), form.getDiscountStart(),
                form.getDiscountEnd(), Instant.now()));
        List<String> stored = new ArrayList<>();
        try {
            if (thumbnail != null) {
                course.setThumbnail(fileStore.put(stored, "courses/" + course.getId() + "/thumbnail/",
                        form.getThumbnail(), thumbnail), thumbnail.detectedMimeType());
            }
            return courseRepository.save(course);
        } catch (RuntimeException e) {
            stored.forEach(fileStore::deleteQuietly);
            throw e;
        }
    }

    @Transactional
    public Course update(UUID id, EditForm form) {
        Course course = find(id);
        course.setTitle(form.getTitle());
        course.setDescription(form.getDescription());
        course.setCategory(form.getCategory());
        course.setUpdatedAt(Instant.now());
        return course;
    }

    @Transactional
    public Course updatePricing(UUID id, Integer price, Integer percent, Instant start, Instant end) {
        Course course = find(id);
        course.setPricing(CoursePricing.check(price, percent, start, end, Instant.now()));
        return course;
    }

    public Course replaceThumbnail(UUID id, MultipartFile file) {
        Course course = find(id);
        ValidatedFile validated = fileValidator.validateThumbnail(file);
        String previous = course.getThumbnailKey();

        List<String> stored = new ArrayList<>();
        try {
            course.setThumbnail(fileStore.put(stored, "courses/" + id + "/thumbnail/", file, validated),
                    validated.detectedMimeType());
            course.setUpdatedAt(Instant.now());
            Course saved = courseRepository.save(course);
            fileStore.deleteQuietly(previous);
            return saved;
        } catch (RuntimeException e) {
            stored.forEach(fileStore::deleteQuietly);
            throw e;
        }
    }

    @Transactional
    public Course setStatus(UUID id, CourseStatus status) {
        Course course = find(id);
        if (status == CourseStatus.PUBLISHED && lessonRepository.countByCourseId(id) == 0) {
            throw new CourseStructureException("Add at least one lesson before publishing this course.");
        }
        course.setStatus(status);
        course.setUpdatedAt(Instant.now());
        return course;
    }

    /** Row first, then storage: a leftover object is harmless, a row pointing at a missing file is not. */
    public void delete(UUID id) {
        Course course = find(id);
        List<String> keys = new ArrayList<>();
        keys.add(course.getThumbnailKey());
        for (Lesson lesson : lessonRepository.findByCourseId(id)) {
            keys.add(lesson.getVideoKey());
            keys.add(lesson.getTranscriptKey());
        }
        materialRepository.findByCourseIdOrderByCreatedAtAsc(id).forEach(m -> keys.add(m.getStorageKey()));
        courseRepository.delete(course);
        keys.forEach(fileStore::deleteQuietly);
    }

    @Transactional
    public void recordView(UUID id) {
        courseRepository.recordView(id, Instant.now());
    }

    public Course find(UUID id) {
        return courseRepository.findById(id).orElseThrow(() -> new CourseNotFoundException(id));
    }

    private boolean present(MultipartFile file) {
        return file != null && !file.isEmpty();
    }
}
