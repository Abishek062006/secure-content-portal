package com.secureportal.common;

import com.secureportal.content.ContentType;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;

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

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> IMAGE_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long THUMBNAIL_MAX_BYTES = 5L * 1024 * 1024;
    private static final long TRANSCRIPT_MAX_BYTES = 2L * 1024 * 1024;

    public ValidatedFile validate(MultipartFile file, ContentType expectedType) {
        return validate(file, expectedType.getLabel(), expectedType.getAllowedExtensions(),
                expectedType.getAllowedMimeTypes(), expectedType.getMaxSizeBytes(), expectedType.getMaxSizeLabel());
    }

    public ValidatedFile validateThumbnail(MultipartFile file) {
        return validate(file, "Image", IMAGE_EXTENSIONS, IMAGE_MIME_TYPES, THUMBNAIL_MAX_BYTES, "5 MB");
    }

    /** WebVTT is plain text, so besides extension and size the file must actually open with the WEBVTT signature. */
    public ValidatedFile validateTranscript(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new UploadException("Please choose a file to upload.");
        }
        String originalFilename = cleanFilename(file.getOriginalFilename());
        if (!"vtt".equals(extractExtension(originalFilename))) {
            throw new UploadException("Transcripts must be WebVTT (.vtt) files, like the ones Zoom exports.");
        }
        if (file.getSize() > TRANSCRIPT_MAX_BYTES) {
            throw new UploadException("Transcript files must be 2 MB or smaller.");
        }
        try (InputStream in = file.getInputStream()) {
            String head = new String(in.readNBytes(16), StandardCharsets.UTF_8).replace("\uFEFF", "");
            if (!head.startsWith("WEBVTT")) {
                throw new UploadException("This file doesn't look like a WebVTT transcript (it should start with WEBVTT).");
            }
        } catch (IOException e) {
            throw new UploadException("Could not read the uploaded file. Please try again.");
        }
        return new ValidatedFile(originalFilename, "text/vtt", file.getSize());
    }

    private ValidatedFile validate(MultipartFile file, String label, Collection<String> allowedExtensions,
                                   Set<String> allowedMimeTypes, long maxSizeBytes, String maxSizeLabel) {
        if (file == null || file.isEmpty()) {
            throw new UploadException("Please choose a file to upload.");
        }

        String originalFilename = cleanFilename(file.getOriginalFilename());
        String extension = extractExtension(originalFilename);

        if (!allowedExtensions.contains(extension)) {
            throw new UploadException(
                    "\"." + extension + "\" isn't a supported " + label.toLowerCase(Locale.ROOT)
                            + " file. Allowed: " + String.join(", ", allowedExtensions));
        }

        if (file.getSize() > maxSizeBytes) {
            throw new UploadException(label + " files must be " + maxSizeLabel + " or smaller.");
        }

        String detectedMimeType = detectMimeType(file);
        if (!allowedMimeTypes.contains(detectedMimeType)) {
            throw new UploadException(
                    "This file's contents don't match a " + label.toLowerCase(Locale.ROOT)
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
