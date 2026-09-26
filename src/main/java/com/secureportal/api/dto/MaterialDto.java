package com.secureportal.api.dto;

import com.secureportal.course.CourseMaterial;

import java.util.UUID;

/** {@code url} is set for links only; {@code pageCount} for PDFs; {@code downloadable} is the admin's choice. */
public record MaterialDto(UUID id, UUID moduleId, String title, String description, String kind, boolean downloadable,
                          String sizeLabel, String filename, String url, Integer pageCount) {

    public static MaterialDto of(CourseMaterial m) {
        return new MaterialDto(m.getId(), m.getModuleId(), m.getTitle(), m.getDescription(), m.getKind().name(),
                m.isDownloadable(), m.getSizeLabel(), m.getFilename(), m.getUrl(), m.getPageCount());
    }
}
