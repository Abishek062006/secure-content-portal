package com.secureportal.common;

import com.secureportal.content.ContentType;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Three independent checks, all required: extension, size, and the file's
 * actual magic bytes. Extension and the browser-supplied {@code Content-Type}
 * are both attacker-controlled, so the byte-level check is the one that
 * actually matters — a renamed executable fails it even though the other two
 * checks might pass.
 */
@Component
public class FileValidator {

    private final Tika tika = new Tika();

    public ValidatedFile validate(MultipartFile file, ContentType expectedType) {
        if (file == null || file.isEmpty()) {
            throw new UploadException("Please choose a file to upload.");
        }

        String originalFilename = cleanFilename(file.getOriginalFilename());
        String extension = extractExtension(originalFilename);

        if (!expectedType.getAllowedExtensions().contains(extension)) {
            throw new UploadException(
                    "\"." + extension + "\" isn't a supported " + expectedType.getLabel().toLowerCase(Locale.ROOT)
                            + " file. Allowed: " + String.join(", ", expectedType.getAllowedExtensions()));
        }

        if (file.getSize() > expectedType.getMaxSizeBytes()) {
            throw new UploadException(
                    expectedType.getLabel() + " files must be " + expectedType.getMaxSizeLabel() + " or smaller.");
        }

        String detectedMimeType = detectMimeType(file);
        if (!expectedType.getAllowedMimeTypes().contains(detectedMimeType)) {
            throw new UploadException(
                    "This file's contents don't match a " + expectedType.getLabel().toLowerCase(Locale.ROOT)
                            + " (detected: " + detectedMimeType
                            + "). Renaming a file's extension does not change what it actually is.");
        }

        return new ValidatedFile(originalFilename, detectedMimeType, file.getSize());
    }

    private String detectMimeType(MultipartFile file) {
        // Content-only detection: the filename and the browser's Content-Type
        // header are never consulted, only the file's actual leading bytes.
        try (InputStream in = file.getInputStream()) {
            return tika.detect(in);
        } catch (IOException e) {
            throw new UploadException("Could not read the uploaded file. Please try again.");
        }
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new UploadException("The file needs a valid extension.");
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String cleanFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new UploadException("The uploaded file has no name.");
        }
        String name = filename.replace('\\', '/');
        int lastSlash = name.lastIndexOf('/');
        return lastSlash >= 0 ? name.substring(lastSlash + 1) : name;
    }
}
