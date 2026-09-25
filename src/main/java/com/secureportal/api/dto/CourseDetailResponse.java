package com.secureportal.api.dto;

import com.secureportal.course.dto.TranscriptCue;

import java.util.List;

/** A course plus a fresh, session-bound video ticket and its parsed transcript (empty if none was uploaded). */
public record CourseDetailResponse(CourseDto course, String ticket, List<TranscriptCue> transcript) {
}
