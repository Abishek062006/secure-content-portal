package com.secureportal.api.dto;

import java.util.List;
import java.util.UUID;

/** A course with its modules and lessons — what the learner's course page and the admin editor both render.
 *  {@code registrationStatus} is null unless the course is REGISTER-type and this learner has sent a
 *  request (then "PENDING", "APPROVED" or "DENIED" — though APPROVED implies {@code enrolled} is already
 *  true, since approving a request enrolls them). */
public record CourseOutlineDto(
        CourseDto course,
        List<ModuleDto> modules,
        boolean enrolled,
        int progressPercent,
        UUID resumeLessonId,
        AssessmentSummaryDto finalAssessment,
        String registrationStatus
) {
    /** {@code locked} (with {@code lockedReason}) applies to learners only; {@code assessment} is the module's quiz or assessment, if it has one. */
    public record ModuleDto(UUID id, String title, String description, int position, List<LessonDto> lessons,
                            boolean locked, String lockedReason, AssessmentSummaryDto assessment,
                            List<MaterialDto> materials) {
    }

    /**
     * {@code completed}/{@code positionSeconds} are the learner's own progress (null for admins);
     * {@code videoFilename}/{@code transcriptFilename} are admin-only.
     */
    public record LessonDto(
            UUID id,
            String title,
            String description,
            int position,
            boolean hasTranscript,
            String videoSizeLabel,
            String videoFilename,
            String transcriptFilename,
            Boolean completed,
            Integer positionSeconds,
            String hlsStatus,
            String hlsMessage
    ) {
    }
}
