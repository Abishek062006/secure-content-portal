package com.secureportal.quiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.ai.AiException;
import com.secureportal.course.dto.TranscriptCue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class QuestionGenerationServiceTest {

    private final QuestionGenerationService service = new QuestionGenerationService(
            mock(com.secureportal.ai.LlmClient.class), mock(com.secureportal.course.CourseStructureService.class),
            mock(QuestionRepository.class), new ObjectMapper());
    private final UUID courseId = UUID.randomUUID();
    private final UUID lessonId = UUID.randomUUID();

    private static String question(String text, String difficulty, String options, int correct, int seconds) {
        return "{\"question\":\"" + text + "\",\"difficulty\":\"" + difficulty + "\",\"options\":" + options
                + ",\"correctIndex\":" + correct + ",\"timestampSeconds\":" + seconds + ",\"explanation\":\"Because.\"}";
    }

    private static final String FOUR = "[\"A\",\"B\",\"C\",\"D\"]";

    @Test
    void keepsValidQuestionsAndShufflesWithoutLosingTheCorrectAnswer() {
        String reply = "{\"questions\":[" + question("What is HMAC?", "easy", FOUR, 2, 30) + "]}";

        List<Question> parsed = service.parse(reply, courseId, lessonId, 600);

        assertThat(parsed).hasSize(1);
        Question q = parsed.get(0);
        assertThat(q.getDifficulty()).isEqualTo(Difficulty.EASY);
        assertThat(q.getStatus()).isEqualTo(QuestionStatus.DRAFT);
        assertThat(q.getSource()).isEqualTo(QuestionSource.AI);
        assertThat(q.getLessonId()).isEqualTo(lessonId);
        assertThat(q.getSourceSeconds()).isEqualTo(30);
        assertThat(q.getOptions()).hasSize(4);
        assertThat(q.getOptions().stream().filter(QuestionOption::isCorrect).map(QuestionOption::getText))
                .containsExactly("C");
    }

    @Test
    void dropsMalformedQuestionsButKeepsTheRest() {
        String reply = "{\"questions\":["
                + question("Good one?", "MEDIUM", FOUR, 0, 10) + ","
                + question("Three options?", "EASY", "[\"A\",\"B\",\"C\"]", 0, 10) + ","
                + question("Bad index?", "EASY", FOUR, 7, 10) + ","
                + question("Bad difficulty?", "IMPOSSIBLE", FOUR, 0, 10) + ","
                + question("Duplicate options?", "HARD", "[\"A\",\"a\",\"C\",\"D\"]", 0, 10) + ","
                + question("   ", "EASY", FOUR, 0, 10)
                + "]}";

        assertThat(service.parse(reply, courseId, lessonId, 600)).extracting(Question::getText).containsExactly("Good one?");
    }

    @Test
    void ignoresATimestampOutsideTheLecture() {
        String reply = "{\"questions\":[" + question("Late?", "EASY", FOUR, 0, 9999) + "]}";

        assertThat(service.parse(reply, courseId, lessonId, 600).get(0).getSourceSeconds()).isNull();
    }

    @Test
    void toleratesMarkdownFencesAroundTheJson() {
        String reply = "Here you go:\n```json\n{\"questions\":[" + question("Fenced?", "EASY", FOUR, 1, 5) + "]}\n```";

        assertThat(service.parse(reply, courseId, lessonId, 600)).hasSize(1);
    }

    @Test
    void rejectsAReplyThatIsNotJson() {
        assertThatThrownBy(() -> service.parse("Sorry, I can't do that.", courseId, lessonId, 600))
                .isInstanceOf(AiException.class);
    }

    @Test
    void splitsALongTranscriptIntoChunksWithTimestamps() {
        List<TranscriptCue> cues = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            cues.add(new TranscriptCue(i * 5, i * 5 + 4, "This is sentence number " + i + " of the lecture, padded out a bit."));
        }

        List<String> chunks = QuestionGenerationService.chunk(cues);

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(QuestionGenerationService.CHUNK_CHARS + 200));
        assertThat(chunks.get(0)).startsWith("[0] This is sentence number 0");
    }

    @Test
    void spreadsTheQuestionCountAcrossChunks() {
        assertThat(QuestionGenerationService.allocate(10, 3)).containsExactly(4, 3, 3);
        assertThat(QuestionGenerationService.allocate(2, 5)).containsExactly(1, 0, 1, 0, 0);
        assertThat(QuestionGenerationService.allocate(7, 1)).containsExactly(7);
    }
}
