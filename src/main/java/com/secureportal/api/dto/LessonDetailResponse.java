package com.secureportal.api.dto;

import com.secureportal.course.dto.TranscriptCue;

import java.util.List;
import java.util.UUID;

/** Everything the lesson player needs: a fresh session-bound video ticket, the transcript, and where to go next. */
public record LessonDetailResponse(
        UUID courseId,
        String courseTitle,
        UUID moduleId,
        String moduleTitle,
        CourseOutlineDto.LessonDto lesson,
        String ticket,
        List<TranscriptCue> transcript,
        UUID previousLessonId,
        UUID nextLessonId,
        int resumeSeconds,
        boolean completed,
        boolean hlsReady
) {
}
