package com.secureportal.hackathon;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Builds the .ics calendar file for a hackathon, so a learner can add it to Apple, Google or Outlook Calendar. */
public final class HackathonCalendar {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final Duration DEFAULT_LENGTH = Duration.ofDays(1);

    private HackathonCalendar() {
    }

    /** The event as an iCalendar document. An event with no start date can't be placed on a calendar. */
    public static String ics(Hackathon hackathon) {
        Instant start = hackathon.getEventStartDate();
        if (start == null) {
            throw new InvalidHackathonException("This hackathon doesn't have a date yet.");
        }
        Instant end = hackathon.getEventEndDate() != null && hackathon.getEventEndDate().isAfter(start)
                ? hackathon.getEventEndDate() : start.plus(DEFAULT_LENGTH);

        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//GradientNovaAI//Hackathons//EN");
        lines.add("CALSCALE:GREGORIAN");
        lines.add("METHOD:PUBLISH");
        lines.add("BEGIN:VEVENT");
        lines.add("UID:hackathon-" + hackathon.getId() + "@gradientnova.ai");
        lines.add("DTSTAMP:" + STAMP.format(Instant.now()));
        lines.add("DTSTART:" + STAMP.format(start));
        lines.add("DTEND:" + STAMP.format(end));
        lines.add("SUMMARY:" + escape(hackathon.getTitle()));
        StringBuilder description = new StringBuilder();
        if (hackathon.getOrganizer() != null) {
            description.append("Organised by ").append(hackathon.getOrganizer()).append("\n");
        }
        if (hackathon.getPrizePool() != null) {
            description.append("Prize pool: ").append(hackathon.getPrizePool()).append("\n");
        }
        if (hackathon.getRegistrationDeadline() != null) {
            description.append("Registration closes: ").append(STAMP.format(hackathon.getRegistrationDeadline())).append("\n");
        }
        description.append("Register: ").append(hackathon.getRegistrationUrl());
        lines.add("DESCRIPTION:" + escape(description.toString()));
        lines.add("URL:" + hackathon.getRegistrationUrl());
        if (hackathon.getLocation() != null) {
            lines.add("LOCATION:" + escape(hackathon.getLocation()));
        }
        lines.add("END:VEVENT");
        lines.add("END:VCALENDAR");

        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(fold(line)).append("\r\n");
        }
        return out.toString();
    }

    /** Text values must escape backslashes, semicolons, commas and newlines, and can never contain a bare line break. */
    static String escape(String text) {
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    /** Lines longer than 75 characters continue on the next line, which starts with one space. */
    static String fold(String line) {
        if (line.length() <= 75) {
            return line;
        }
        StringBuilder folded = new StringBuilder(line.substring(0, 75));
        for (int i = 75; i < line.length(); i += 74) {
            folded.append("\r\n ").append(line, i, Math.min(line.length(), i + 74));
        }
        return folded.toString();
    }
}
