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

    @Test
    void generatingAHundredHardQuestionsAsksInBatchesKeepsOnlyHardOnesAndTopsUp() {
        var llm = mock(com.secureportal.ai.LlmClient.class);
        var structure = mock(com.secureportal.course.CourseStructureService.class);
        var repository = mock(QuestionRepository.class);
        var lesson = mock(com.secureportal.course.Lesson.class);
        org.mockito.Mockito.when(lesson.getCourseId()).thenReturn(courseId);
        org.mockito.Mockito.when(structure.findLesson(lessonId)).thenReturn(lesson);
        org.mockito.Mockito.when(structure.transcript(lesson)).thenReturn(List.of(new TranscriptCue(0, 60, "Short lecture.")));
        org.mockito.Mockito.when(repository.findByLessonIdOrderByCreatedAtAsc(lessonId)).thenReturn(List.of());
        org.mockito.Mockito.when(repository.saveAll(org.mockito.ArgumentMatchers.anyList())).thenAnswer(i -> i.getArgument(0));

        int[] serial = {0};
        List<Integer> asked = new ArrayList<>();
        // A model that ignores the difficulty every fifth question and repeats itself in the first reply.
        org.mockito.Mockito.when(llm.complete(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> {
                    String prompt = invocation.getArgument(1);
                    int n = Integer.parseInt(prompt.replaceAll("(?s).*Write exactly (\\d+) .*", "$1"));
                    asked.add(n);
                    assertThat(prompt).contains("Every question must be HARD");
                    StringBuilder sb = new StringBuilder("{\"questions\":[");
                    for (int i = 0; i < n; i++) {
                        serial[0]++;
                        if (i > 0) {
                            sb.append(',');
                        }
                        sb.append(question("Question number " + (asked.size() == 1 && i == 1 ? 1 : serial[0]) + "?",
                                serial[0] % 5 == 0 ? "EASY" : "HARD", FOUR, 0, 10));
                    }
                    return sb.append("]}").toString();
                });

        List<Question> made = new QuestionGenerationService(llm, structure, repository, new ObjectMapper())
                .generate(lessonId, 100, Difficulty.HARD, true);

        assertThat(made).hasSize(100).allSatisfy(q -> {
            assertThat(q.getDifficulty()).isEqualTo(Difficulty.HARD);
            assertThat(q.isFinalOnly()).isTrue();
            assertThat(q.getStatus()).isEqualTo(QuestionStatus.DRAFT);
        });
        assertThat(made.stream().map(Question::getText).distinct().count()).isEqualTo(100);
        assertThat(asked).allSatisfy(n -> assertThat(n).isLessThanOrEqualTo(QuestionGenerationService.BATCH));
        assertThat(asked.size()).isGreaterThan(5);
    }

    @Test
    void aMixedRequestKeepsEveryDifficultyAndAnEmptyRoundStopsTheTopUp() {
        String reply = "{\"questions\":[" + question("Easy one?", "EASY", FOUR, 0, 1) + ","
                + question("Hard one?", "HARD", FOUR, 0, 1) + "]}";

        assertThat(service.parse(reply, courseId, lessonId, 600, null, false)).hasSize(2);
        assertThat(service.parse(reply, courseId, lessonId, 600, Difficulty.EASY, false))
                .extracting(Question::getText).containsExactly("Easy one?");
    }
}
