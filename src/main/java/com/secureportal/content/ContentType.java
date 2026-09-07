package com.secureportal.content;

import java.util.List;
import java.util.Set;

/**
 * The three kinds of content the portal hosts. Each carries its own allow-list
 * of accepted MIME types (verified against the file's magic bytes, not against
 * the browser-supplied Content-Type header) and its own size ceiling.
 */
public enum ContentType {

    VIDEO(Set.of("video/mp4", "video/webm"),
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
