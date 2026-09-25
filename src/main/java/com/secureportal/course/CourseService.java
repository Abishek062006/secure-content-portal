package com.secureportal.course;

import com.secureportal.common.FileValidator;
import com.secureportal.common.ValidatedFile;
import com.secureportal.content.ContentType;
import com.secureportal.content.dto.EditForm;
import com.secureportal.course.dto.CourseUploadForm;
import com.secureportal.course.dto.TranscriptCue;
import com.secureportal.storage.StorageObject;
import com.secureportal.storage.StorageService;
import com.secureportal.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseService.class);

    private final CourseRepository courseRepository;
    private final StorageService storageService;
    private final FileValidator fileValidator;

    public CourseService(CourseRepository courseRepository, StorageService storageService,
                         FileValidator fileValidator) {
        this.courseRepository = courseRepository;
        this.storageService = storageService;
        this.fileValidator = fileValidator;
    }

    /**
     * Every file is validated before any byte is stored, so a bad thumbnail
     * can't leave an orphaned video behind. If a later upload or the row
     * insert still fails, whatever was already stored is removed again.
     */
    public Course create(CourseUploadForm form, User uploadedBy) {
        ValidatedFile video = fileValidator.validate(form.getVideo(), ContentType.VIDEO);
        ValidatedFile thumbnail = present(form.getThumbnail())
                ? fileValidator.validateThumbnail(form.getThumbnail()) : null;
        ValidatedFile transcript = present(form.getTranscript())
                ? fileValidator.validateTranscript(form.getTranscript()) : null;

        String base = "courses/" + UUID.randomUUID();
        List<String> stored = new ArrayList<>();
        try {
            String videoKey = store(stored, base + "/video/", form.getVideo(), video);
            Course course = new Course(form.getTitle(), form.getDescription(), form.getCategory(),
                    videoKey, video.originalFilename(), video.detectedMimeType(), video.sizeBytes(), uploadedBy);

            if (thumbnail != null) {
                course.setThumbnail(store(stored, base + "/thumbnail/", form.getThumbnail(), thumbnail),
                        thumbnail.detectedMimeType());
            }
            if (transcript != null) {
                course.setTranscript(store(stored, base + "/transcript/", form.getTranscript(), transcript),
                        transcript.originalFilename());
            }
            return courseRepository.save(course);
        } catch (RuntimeException e) {
            stored.forEach(this::deleteQuietly);
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

    public Course replaceThumbnail(UUID id, MultipartFile file) {
        Course course = find(id);
        ValidatedFile validated = fileValidator.validateThumbnail(file);
        String previous = course.getThumbnailKey();

        List<String> stored = new ArrayList<>();
        try {
            String key = store(stored, "courses/" + UUID.randomUUID() + "/thumbnail/", file, validated);
            course.setThumbnail(key, validated.detectedMimeType());
            course.setUpdatedAt(Instant.now());
            Course saved = courseRepository.save(course);
            deleteQuietly(previous);
            return saved;
        } catch (RuntimeException e) {
            stored.forEach(this::deleteQuietly);
            throw e;
        }
    }

    public Course replaceTranscript(UUID id, MultipartFile file) {
        Course course = find(id);
        ValidatedFile validated = fileValidator.validateTranscript(file);
        String previous = course.getTranscriptKey();

        List<String> stored = new ArrayList<>();
        try {
            String key = store(stored, "courses/" + UUID.randomUUID() + "/transcript/", file, validated);
            course.setTranscript(key, validated.originalFilename());
            course.setUpdatedAt(Instant.now());
            Course saved = courseRepository.save(course);
            deleteQuietly(previous);
            return saved;
        } catch (RuntimeException e) {
            stored.forEach(this::deleteQuietly);
            throw e;
        }
    }

    /** Row first, then storage: a leftover object is harmless, a row pointing at a missing file is not. */
    public void delete(UUID id) {
        Course course = find(id);
        List<String> keys = new ArrayList<>();
        keys.add(course.getVideoKey());
        keys.add(course.getThumbnailKey());
        keys.add(course.getTranscriptKey());
        courseRepository.delete(course);
        keys.forEach(this::deleteQuietly);
    }

    @Transactional
    public void recordView(UUID id) {
        courseRepository.recordView(id, Instant.now());
    }

    public List<TranscriptCue> transcript(Course course) {
        if (course.getTranscriptKey() == null) {
            return List.of();
        }
        try (StorageObject object = storageService.get(course.getTranscriptKey(), null, null)) {
            return VttParser.parse(new String(object.content().readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read transcript for course " + course.getId(), e);
        }
    }

    public Course find(UUID id) {
        return courseRepository.findById(id).orElseThrow(() -> new CourseNotFoundException(id));
    }

    private String store(List<String> stored, String prefix, MultipartFile file, ValidatedFile validated) {
        String key = prefix + validated.originalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
        try (InputStream in = file.getInputStream()) {
            storageService.put(key, in, validated.sizeBytes(), validated.detectedMimeType());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the uploaded file", e);
        }
        stored.add(key);
        return key;
    }

    private boolean present(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            storageService.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete storage object {}", key, e);
        }
    }
}
