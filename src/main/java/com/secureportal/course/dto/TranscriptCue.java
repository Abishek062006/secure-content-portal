package com.secureportal.course.dto;

/** One timed line of a transcript; times are seconds from the start of the video. */
public record TranscriptCue(double start, double end, String text) {
}
