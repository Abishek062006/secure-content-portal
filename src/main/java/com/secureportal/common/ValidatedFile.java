package com.secureportal.common;

/** Result of {@link FileValidator#validate}: the file passed every check. */
public record ValidatedFile(String originalFilename, String detectedMimeType, long sizeBytes) {
}
