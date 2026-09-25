package com.secureportal.course;

import com.secureportal.course.dto.TranscriptCue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Reads WebVTT (what Zoom exports) into timed cues. Header, NOTE and STYLE blocks are skipped. */
public final class VttParser {

    private static final Pattern TAGS = Pattern.compile("<[^>]+>");

    private VttParser() {
    }

    public static List<TranscriptCue> parse(String vtt) {
        String normalised = vtt.replace("﻿", "").replace("\r\n", "\n").replace('\r', '\n');
        List<TranscriptCue> cues = new ArrayList<>();

        for (String block : normalised.split("\n\\s*\n")) {
            String[] lines = block.split("\n");
            int timing = -1;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains("-->")) {
                    timing = i;
                    break;
                }
            }
            if (timing < 0) {
                continue;
            }

            String[] range = lines[timing].split("-->");
            Double start = seconds(range[0]);
            Double end = seconds(range[1].trim().split("\\s+")[0]);
            if (start == null || end == null) {
                continue;
            }

            StringBuilder text = new StringBuilder();
            for (int i = timing + 1; i < lines.length; i++) {
                String line = TAGS.matcher(lines[i]).replaceAll("").trim();
                if (!line.isEmpty()) {
                    text.append(text.isEmpty() ? "" : " ").append(line);
                }
            }
            if (!text.isEmpty()) {
                cues.add(new TranscriptCue(start, end, text.toString()));
            }
        }
        return cues;
    }

    private static Double seconds(String timestamp) {
        String[] parts = timestamp.trim().replace(',', '.').split(":");
        try {
            double total = 0;
            for (String part : parts) {
                total = total * 60 + Double.parseDouble(part);
            }
            return parts.length == 2 || parts.length == 3 ? total : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
