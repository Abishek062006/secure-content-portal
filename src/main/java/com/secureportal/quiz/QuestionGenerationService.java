package com.secureportal.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.ai.AiException;
import com.secureportal.ai.LlmClient;
import com.secureportal.course.CourseStructureService;
import com.secureportal.course.Lesson;
import com.secureportal.course.dto.TranscriptCue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a lesson's transcript into draft multiple-choice questions. Model
 * output is never trusted: every question goes through {@link QuestionFactory}
 * (options are shuffled there, since models tend to put the right answer
 * first) and is saved as DRAFT for an admin to review before any learner sees it.
 */

@Service
public class QuestionGenerationService {

    static final int CHUNK_CHARS = 12_000;

    private static final Logger log = LoggerFactory.getLogger(QuestionGenerationService.class);

    private static final String SYSTEM_PROMPT =
            "You write multiple-choice quiz questions for a training course from a lecture transcript. "
                    + "Respond with a single JSON object and nothing else.";

    private final LlmClient llmClient;
    private final CourseStructureService structureService;
    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;

    public QuestionGenerationService(LlmClient llmClient, CourseStructureService structureService,
                                     QuestionRepository questionRepository, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.structureService = structureService;
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
    }

    /** Most questions asked of the model in one call: bigger asks get slow, truncated or repetitive. */
    static final int BATCH = 20;
    private static final int TOP_UP_ROUNDS = 5;

    public List<Question> generate(UUID lessonId, int count) {
        return generate(lessonId, count, null, false);
    }

    /**
     * @param only  when set, every question is written at that difficulty (others the model slips in are dropped);
     *              null asks for a mix
     * @param finalOnly save them as new questions for the final assessment rather than the module quizzes
     */
    public List<Question> generate(UUID lessonId, int count, Difficulty only, boolean finalOnly) {
        Lesson lesson = structureService.findLesson(lessonId);
        List<TranscriptCue> cues = structureService.transcript(lesson);
        if (cues.isEmpty()) {
            throw new QuestionGenerationException(
                    "Upload a transcript (.vtt) for this lesson first — questions are generated from it.");
        }

        double lastSecond = cues.get(cues.size() - 1).end();
        // Free tiers cap tokens per minute, and every call re-sends its transcript excerpt. So instead of asking
        // for all the questions from big shared chunks, give each batch of ~BATCH questions its own slice.
        List<String> chunks = windows(cues, Math.max((count + BATCH - 1) / BATCH, chunk(cues).size()));

        Set<String> seen = new HashSet<>();
        questionRepository.findByLessonIdOrderByCreatedAtAsc(lessonId)
                .forEach(existing -> seen.add(existing.getText().toLowerCase(Locale.ROOT)));

        List<Question> accepted = new ArrayList<>();
        AiException lastFailure = null;
        // The model often returns fewer usable, non-duplicate questions than asked for, so ask again for the
        // shortfall a few times, stopping as soon as a round adds nothing.
        for (int round = 0; round < TOP_UP_ROUNDS && accepted.size() < count; round++) {
            int before = accepted.size();
            int[] perChunk = allocate(count - accepted.size(), chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                for (int remaining = perChunk[i]; remaining > 0; remaining -= BATCH) {
                    int ask = Math.min(BATCH, remaining);
                    try {
                        String reply = llmClient.complete(SYSTEM_PROMPT, userPrompt(ask, chunks.get(i), only, round));
                        for (Question question : parse(reply, lesson.getCourseId(), lessonId, lastSecond, only, finalOnly)) {
                            if (accepted.size() < count && seen.add(question.getText().toLowerCase(Locale.ROOT))) {
                                accepted.add(question);
                            }
                        }
                    } catch (AiException e) {
                        log.warn("Question generation failed for chunk {} of lesson {}", i, lessonId, e);
                        lastFailure = e;
                        break;
                    }
                }
            }
            if (accepted.size() == before) {
                break;
            }
        }

        if (accepted.isEmpty()) {
            if (lastFailure != null) {
                throw lastFailure;
            }
            throw new QuestionGenerationException("The AI didn't return any new usable questions. Try again.");
        }
        return questionRepository.saveAll(accepted);
    }

    static List<String> chunk(List<TranscriptCue> cues) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (TranscriptCue cue : cues) {
            String line = "[" + (int) cue.start() + "] " + cue.text() + "\n";
            if (current.length() + line.length() > CHUNK_CHARS && !current.isEmpty()) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            current.append(line);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    /** Splits the transcript into {@code parts} excerpts of roughly equal length, each line keeping its timestamp. */
    static List<String> windows(List<TranscriptCue> cues, int parts) {
        List<String> lines = new ArrayList<>();
        long total = 0;
        for (TranscriptCue cue : cues) {
            String line = "[" + (int) cue.start() + "] " + cue.text() + "\n";
            lines.add(line);
            total += line.length();
        }
        int n = Math.max(1, Math.min(parts, lines.size()));
        List<String> windows = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        long target = (total + n - 1) / n;
        for (String line : lines) {
            if (current.length() >= target && windows.size() < n - 1) {
                windows.add(current.toString());
                current.setLength(0);
            }
            current.append(line);
        }
        if (!current.isEmpty()) {
            windows.add(current.toString());
        }
        return windows;
    }

    /** Spreads {@code count} questions across the chunks; with fewer questions than chunks, picks evenly spaced ones. */
    static int[] allocate(int count, int chunks) {
        int[] perChunk = new int[chunks];
        if (count >= chunks) {
            for (int i = 0; i < chunks; i++) {
                perChunk[i] = count / chunks + (i < count % chunks ? 1 : 0);
            }
        } else {
            for (int i = 0; i < count; i++) {
                perChunk[(int) ((long) i * chunks / count)] = 1;
            }
        }
        return perChunk;
    }

    private String userPrompt(int count, String excerpt, Difficulty only, int round) {
        String difficultyRule = only == null
                ? "- Mix difficulty: EASY (recall a stated fact), MEDIUM (understand or explain), HARD (apply or combine ideas). "
                + "Aim for about 40% EASY, 40% MEDIUM, 20% HARD.\n"
                : "- Every question must be " + only.name() + " difficulty and its \"difficulty\" must be \"" + only.name() + "\". "
                + switch (only) {
                    case EASY -> "EASY means recalling a fact that is stated directly.";
                    case MEDIUM -> "MEDIUM means understanding or explaining an idea, not just recalling it.";
                    case HARD -> "HARD means applying or combining ideas, for example predicting the result of an example.";
                } + "\n";
        return "Write exactly " + count + " multiple-choice questions that test understanding of the lecture excerpt below.\n"
                + "Rules:\n"
                + "- Base every question only on the excerpt; never invent facts.\n"
                + "- Give 4 options per question with exactly one correct; the wrong options must be plausible.\n"
                + difficultyRule
                + "- Each question must ask about something different from the others.\n"
                + (round > 0 ? "- Earlier attempts already covered the obvious points; look for less obvious details.\n" : "")
                + "- \"timestampSeconds\" is the transcript time in seconds where the answer is discussed.\n"
                + "- \"explanation\" is one sentence on why the answer is correct.\n"
                + "Return JSON exactly like: {\"questions\":[{\"question\":\"...\",\"difficulty\":\"EASY\","
                + "\"options\":[\"A\",\"B\",\"C\",\"D\"],\"correctIndex\":0,\"timestampSeconds\":123,\"explanation\":\"...\"}]}\n\n"
                + "Transcript excerpt (each line starts with its time in seconds):\n" + excerpt;
    }

    List<Question> parse(String reply, UUID courseId, UUID lessonId, double lastSecond) {
        return parse(reply, courseId, lessonId, lastSecond, null, false);
    }

    List<Question> parse(String reply, UUID courseId, UUID lessonId, double lastSecond, Difficulty only, boolean finalOnly) {
        JsonNode root;
        try {
            root = objectMapper.readTree(stripFences(reply));
        } catch (Exception e) {
            throw new AiException("The AI didn't return valid JSON.", e);
        }

        List<Question> questions = new ArrayList<>();
        for (JsonNode node : root.path("questions")) {
            JsonNode optionNodes = node.path("options");
            List<String> options = new ArrayList<>();
            if (optionNodes.isArray()) {
                optionNodes.forEach(o -> options.add(o.asText("")));
            }
            Integer seconds = null;
            if (node.path("timestampSeconds").isNumber()) {
                int value = node.path("timestampSeconds").asInt();
                if (value >= 0 && value <= Math.ceil(lastSecond)) {
                    seconds = value;
                }
            }
            try {
                Question question = QuestionFactory.build(courseId, lessonId, QuestionSource.AI, QuestionStatus.DRAFT,
                        node.path("question").asText(""), node.path("difficulty").asText(""), options,
                        node.path("correctIndex").asInt(-1), node.path("explanation").asText(""), seconds, true);
                if (only != null && question.getDifficulty() != only) {
                    continue;
                }
                question.setFinalOnly(finalOnly);
                questions.add(question);
            } catch (InvalidQuestionException e) {
                log.debug("Dropped an invalid AI question: {}", e.getMessage());
            }
        }
        return questions;
    }

    private static String stripFences(String reply) {
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        return start >= 0 && end > start ? reply.substring(start, end + 1) : reply;
    }
}
