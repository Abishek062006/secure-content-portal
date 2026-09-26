package com.secureportal.jobs;

import com.secureportal.ai.AiNotConfiguredException;
import com.secureportal.ai.AiProperties;
import com.secureportal.course.CourseStructureService;
import com.secureportal.course.Lesson;
import com.secureportal.quiz.Difficulty;
import com.secureportal.quiz.QuestionGenerationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Starts question generation as a background job and lets the admin's page follow it. */
@Service
public class GenerationJobService {

    private final GenerationJobRepository jobRepository;
    private final CourseStructureService structureService;
    private final JobDispatcher dispatcher;
    private final AiProperties aiProperties;

    public GenerationJobService(GenerationJobRepository jobRepository, CourseStructureService structureService,
                                JobDispatcher dispatcher, AiProperties aiProperties) {
        this.jobRepository = jobRepository;
        this.structureService = structureService;
        this.dispatcher = dispatcher;
        this.aiProperties = aiProperties;
    }

    /** Problems the admin can fix right now are reported up front rather than as a job that fails a moment later. */
    public GenerationJob submit(UUID lessonId, int count, Difficulty difficulty, boolean finalOnly, Long userId) {
        Lesson lesson = structureService.findLesson(lessonId);
        if (structureService.transcript(lesson).isEmpty()) {
            throw new QuestionGenerationException("Upload a transcript (.vtt) for this lesson first — questions are generated from it.");
        }
        if (!aiProperties.isConfigured()) {
            throw new AiNotConfiguredException();
        }
        if (jobRepository.existsByLessonIdAndStatusIn(lessonId, List.of(JobStatus.QUEUED, JobStatus.RUNNING))) {
            throw new QuestionGenerationException("Questions are already being generated for this lesson. Wait for that to finish.");
        }
        GenerationJob job = jobRepository.save(new GenerationJob(lesson.getCourseId(), lessonId, userId, count, difficulty, finalOnly));
        dispatcher.dispatch(job.getId());
        return job;
    }

    public GenerationJob find(UUID id) {
        return jobRepository.findById(id).orElseThrow(GenerationJobNotFoundException::new);
    }

    public List<GenerationJob> recent(UUID courseId) {
        return jobRepository.findTop10ByCourseIdOrderByCreatedAtDesc(courseId);
    }
}
