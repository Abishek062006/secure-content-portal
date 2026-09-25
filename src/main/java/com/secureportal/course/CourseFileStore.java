package com.secureportal.course;

import com.secureportal.common.ValidatedFile;
import com.secureportal.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Stores course files and remembers what it stored, so a failed request can undo it. */
@Component
class CourseFileStore {

    private static final Logger log = LoggerFactory.getLogger(CourseFileStore.class);

    private final StorageService storageService;

    CourseFileStore(StorageService storageService) {
        this.storageService = storageService;
    }

    String put(List<String> stored, String prefix, MultipartFile file, ValidatedFile validated) {
        String key = prefix + validated.originalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
        try (InputStream in = file.getInputStream()) {
            storageService.put(key, in, validated.sizeBytes(), validated.detectedMimeType());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the uploaded file", e);
        }
        stored.add(key);
        return key;
    }

    void deleteQuietly(String key) {
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
