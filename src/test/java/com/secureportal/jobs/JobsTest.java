package com.secureportal.jobs;

import com.secureportal.ai.AiException;
import com.secureportal.ai.AiNotConfiguredException;
import com.secureportal.ai.AiProperties;
import com.secureportal.course.CourseStructureService;
import com.secureportal.course.Lesson;
import com.secureportal.course.dto.TranscriptCue;
import com.secureportal.quiz.Question;
import com.secureportal.quiz.QuestionGenerationException;
import com.secureportal.quiz.QuestionGenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobsTest {

    private final UUID course = UUID.randomUUID();
    private final UUID lesson = UUID.randomUUID();
    private final GenerationJobRepository repository = mock(GenerationJobRepository.class);
    private final QuestionGenerationService generation = mock(QuestionGenerationService.class);
    private final GenerationJobRunner runner = new GenerationJobRunner(repository, generation);

    private GenerationJob job(int count) {
        GenerationJob job = new GenerationJob(course, lesson, 1L, count, null, false);
        when(repository.findById(job.getId())).thenReturn(java.util.Optional.of(job));
        return job;
    }

    @Test
    void aSuccessfulRunReportsProgressAndFinishes() {
        GenerationJob job = job(3);
        doAnswer(invocation -> {
            IntConsumer progress = invocation.getArgument(4);
            progress.accept(2);
            assertThat(job.getProduced()).isEqualTo(2);
            assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
            return List.of(mock(Question.class), mock(Question.class), mock(Question.class));
        }).when(generation).generate(eq(lesson), eq(3), any(), anyBoolean(), any());

        runner.run(job.getId());

        assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(job.getProduced()).isEqualTo(3);
        assertThat(job.getMessage()).isNull();
    }

    @Test
    void makingFewerThanAskedFinishesWithAnExplanation() {
        GenerationJob job = job(50);
        when(generation.generate(eq(lesson), eq(50), any(), anyBoolean(), any())).thenReturn(List.of(mock(Question.class)));

        runner.run(job.getId());

        assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(job.getMessage()).contains("Made 1 of 50");
    }

    @Test
    void expectedAndUnexpectedFailuresBothEndTheJobAsFailedWithAUsefulMessage() {
        GenerationJob rateLimited = job(5);
        when(generation.generate(eq(lesson), anyInt(), any(), anyBoolean(), any())).thenThrow(new AiException("The AI service answered HTTP 429"));
        runner.run(rateLimited.getId());
        assertThat(rateLimited.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(rateLimited.getMessage()).contains("429");

        GenerationJob crashed = job(5);
        when(generation.generate(eq(lesson), anyInt(), any(), anyBoolean(), any())).thenThrow(new IllegalStateException("boom: internal detail"));
        runner.run(crashed.getId());
        assertThat(crashed.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(crashed.getMessage()).doesNotContain("internal detail");
    }

    @Test
    void aJobThatIsAlreadyDoneOrMissingIsLeftAlone() {
        GenerationJob done = job(1);
        done.finish(1, null);
        runner.run(done.getId());
        runner.run(UUID.randomUUID());

        verify(generation, never()).generate(any(), anyInt(), any(), anyBoolean(), any());
    }

    @Test
    void rabbitPublishesTheJobIdAndTheListenerRunsIt() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        UUID id = UUID.randomUUID();
        new RabbitJobDispatcher(template, new QueueNames("t-")).dispatch(id);
        verify(template).convertAndSend("t-question-generation", id.toString());

        GenerationJobRunner mockRunner = mock(GenerationJobRunner.class);
        new GenerationJobListener(mockRunner).onMessage(id.toString());
        verify(mockRunner).run(id);
    }

    @Test
    void theServiceRefusesWhatCannotWorkBeforeQueueingAnything() {
        CourseStructureService structure = mock(CourseStructureService.class);
        Lesson found = mock(Lesson.class);
        when(found.getCourseId()).thenReturn(course);
        when(structure.findLesson(lesson)).thenReturn(found);
        AiProperties ai = new AiProperties();
        JobDispatcher dispatcher = mock(JobDispatcher.class);
        GenerationJobService service = new GenerationJobService(repository, structure, dispatcher, ai);

        when(structure.transcript(found)).thenReturn(List.of());
        assertThatThrownBy(() -> service.submit(lesson, 5, null, false, 1L)).isInstanceOf(QuestionGenerationException.class);

        when(structure.transcript(found)).thenReturn(List.of(new TranscriptCue(0, 5, "Hello")));
        assertThatThrownBy(() -> service.submit(lesson, 5, null, false, 1L)).isInstanceOf(AiNotConfiguredException.class);

        ai.setModel("m");
        when(repository.existsByLessonIdAndStatusIn(eq(lesson), any())).thenReturn(true);
        assertThatThrownBy(() -> service.submit(lesson, 5, null, false, 1L)).hasMessageContaining("already being generated");
        verify(dispatcher, never()).dispatch(any());

        when(repository.existsByLessonIdAndStatusIn(eq(lesson), any())).thenReturn(false);
        when(repository.save(any(GenerationJob.class))).thenAnswer(i -> i.getArgument(0));
        GenerationJob queued = service.submit(lesson, 5, null, false, 1L);
        assertThat(queued.getStatus()).isEqualTo(JobStatus.QUEUED);
        verify(dispatcher).dispatch(queued.getId());
    }

    @Test
    void aRestartFailsJobsThatWereLeftRunningBecauseTheyCannotBeResumedLocally() {
        GenerationJob running = new GenerationJob(course, lesson, 1L, 5, null, false);
        running.start();
        when(repository.findByStatusIn(any())).thenReturn(List.of(running));

        new LocalJobDispatcher(runner, repository).failInterruptedJobs();

        assertThat(running.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(running.getMessage()).contains("restarted");
        verify(repository).saveAll(List.of(running));
    }
}
