package com.secureportal.content;

import java.util.List;
import java.util.Set;

/**
 * The three kinds of content the portal hosts. Each carries its own allow-list
 * of accepted MIME types (verified against the file's magic bytes, not against
 * the browser-supplied Content-Type header) and its own size ceiling.
 */
public enum ContentType {

    /**
     * Tika-core's magic-byte detector reads the ISO-BMFF {@code ftyp} box but
     * only classifies MP4 as {@code video/mp4} for a handful of major brands
     * (e.g. {@code mp42}); very common real-world exports with major brand
     * {@code isom} (ffmpeg, iOS/macOS) are classified {@code video/quicktime}
     * instead — confirmed empirically, since the two formats share a
     * container family and full brand disambiguation needs the heavier
     * tika-parsers modules we deliberately left off the classpath. Likewise a
     * WebM file's EBML header is indistinguishable from a plain Matroska file
     * without deep-parsing the DocType element, so it detects as
     * {@code application/x-matroska}. All four values are still genuine,
     * unambiguous video-container signatures — a text file or executable
     * renamed to {@code .mp4} detects as neither.
     */
    VIDEO(Set.of("video/mp4", "video/quicktime", "application/x-matroska", "video/x-matroska"),
            List.of("mp4", "webm"),
            512L * 1024 * 1024),

    PDF(Set.of("application/pdf"),
            List.of("pdf"),
            32L * 1024 * 1024),

    HTML(Set.of("text/html", "text/plain", "application/xhtml+xml"),
            List.of("html", "htm"),
            2L * 1024 * 1024);

    private final Set<String> allowedMimeTypes;
    private final List<String> allowedExtensions;
    private final long maxSizeBytes;

    ContentType(Set<String> allowedMimeTypes, List<String> allowedExtensions, long maxSizeBytes) {
        this.allowedMimeTypes = allowedMimeTypes;
        this.allowedExtensions = allowedExtensions;
        this.maxSizeBytes = maxSizeBytes;
    }

    public Set<String> getAllowedMimeTypes() {
        return allowedMimeTypes;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public String getMaxSizeLabel() {
        return (maxSizeBytes / (1024 * 1024)) + " MB";
    }

    /** Comma-separated extension list, for the file picker's {@code accept} attribute. */
    public String getAcceptAttribute() {
        return allowedExtensions.stream().map(ext -> "." + ext).reduce((a, b) -> a + "," + b).orElse("");
    }

    public String getLabel() {
        return switch (this) {
            case VIDEO -> "Video";
            case PDF -> "PDF";
            case HTML -> "HTML page";
        };
    }
}
