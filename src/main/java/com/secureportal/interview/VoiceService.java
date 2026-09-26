package com.secureportal.interview;

import com.secureportal.ai.SpeechToText;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spoken interview answers. A recording is checked, sent to the speech service and thrown away: it is never stored, only the
 * transcript goes back to the learner, who reads and edits it before it counts as their answer. Each learner has a daily cap.
 */
@Service
public class VoiceService {

    static final int MAX_BYTES = 4 * 1024 * 1024;
    public static final int MAX_BYTES_ALLOWED = MAX_BYTES;
    static final int MAX_SECONDS = 180;
    static final int MAX_PER_DAY = 40;

    private static final Pattern FILLER = Pattern.compile(
            "\\b(?:um+|uh+|erm?|you know|i mean|sort of|kind of|basically)\\b", Pattern.CASE_INSENSITIVE);

    private final SpeechToText speech;
    private final JdbcTemplate jdbc;

    public VoiceService(SpeechToText speech, JdbcTemplate jdbc) {
        this.speech = speech;
        this.jdbc = jdbc;
    }

    /** What was said, plus how it was said: informational only, shown to the learner and never stored. */
    public record Transcript(String text, int words, int fillerWords, Integer wordsPerMinute) {
    }

    public record Usage(int used, int limit) {
    }

    /** What kind of recording this is, judged by its first bytes rather than by what the browser claims. */
    record AudioKind(String contentType, String extension) {
    }

    public Transcript transcribe(Long userId, byte[] audio, int durationSeconds) {
        if (audio == null || audio.length == 0) {
            throw new InvalidInterviewException("No recording was received.");
        }
        if (audio.length > MAX_BYTES) {
            throw new InvalidInterviewException("That recording is too long. Keep each answer under " + (MAX_SECONDS / 60) + " minutes.");
        }
        AudioKind kind = kindOf(audio);
        if (kind == null) {
            throw new InvalidInterviewException("That doesn't look like an audio recording.");
        }

        if (!reserve(userId)) {
            throw new InterviewLimitException(MAX_PER_DAY, "voice answers");
        }
        try {
            String text = speech.transcribe(audio, "answer." + kind.extension(), kind.contentType());
            if (text == null || text.isBlank()) {
                throw new InvalidInterviewException("We couldn't hear anything in that recording. Check your microphone and try again.");
            }
            return delivery(text, durationSeconds);
        } catch (RuntimeException e) {
            release(userId);
            throw e;
        }
    }

    public Usage usage(Long userId) {
        Integer used = jdbc.query("SELECT clips FROM interview_voice_usage WHERE user_id = ? AND day = ?",
                rs -> rs.next() ? rs.getInt(1) : 0, userId, today());
        return new Usage(used == null ? 0 : used, MAX_PER_DAY);
    }

    // ---- The daily cap: counted in the database, so it holds across restarts and several servers ---------------------------

    private boolean reserve(Long userId) {
        jdbc.update("INSERT INTO interview_voice_usage (user_id, day, clips) VALUES (?, ?, 0) ON DUPLICATE KEY UPDATE clips = clips", userId, today());
        return jdbc.update("UPDATE interview_voice_usage SET clips = clips + 1 WHERE user_id = ? AND day = ? AND clips < ?",
                userId, today(), MAX_PER_DAY) == 1;
    }

    /** A recording that couldn't be transcribed doesn't use up the learner's allowance. */
    private void release(Long userId) {
        jdbc.update("UPDATE interview_voice_usage SET clips = GREATEST(clips - 1, 0) WHERE user_id = ? AND day = ?", userId, today());
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    // ---- Helpers ----------------------------------------------------------------------------------------------------------

    static Transcript delivery(String text, int durationSeconds) {
        int words = text.isBlank() ? 0 : text.strip().split("\\s+").length;
        int fillers = 0;
        Matcher matcher = FILLER.matcher(text);
        while (matcher.find()) {
            fillers++;
        }
        int seconds = Math.min(Math.max(durationSeconds, 0), MAX_SECONDS);
        Integer pace = seconds >= 5 ? (int) Math.round(words * 60.0 / seconds) : null;
        return new Transcript(text, words, fillers, pace);
    }

    static AudioKind kindOf(byte[] d) {
        if (d.length >= 4 && (d[0] & 0xFF) == 0x1A && (d[1] & 0xFF) == 0x45 && (d[2] & 0xFF) == 0xDF && (d[3] & 0xFF) == 0xA3) {
            return new AudioKind("audio/webm", "webm");
        }
        if (d.length >= 4 && d[0] == 'O' && d[1] == 'g' && d[2] == 'g' && d[3] == 'S') {
            return new AudioKind("audio/ogg", "ogg");
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'A' && d[10] == 'V' && d[11] == 'E') {
            return new AudioKind("audio/wav", "wav");
        }
        if (d.length >= 8 && d[4] == 'f' && d[5] == 't' && d[6] == 'y' && d[7] == 'p') {
            return new AudioKind("audio/mp4", "m4a");
        }
        if (d.length >= 3 && d[0] == 'I' && d[1] == 'D' && d[2] == '3') {
            return new AudioKind("audio/mpeg", "mp3");
        }
        return null;
    }
}
