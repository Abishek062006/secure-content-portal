package com.secureportal.jobs;

import com.secureportal.ai.AiException;
import com.secureportal.ai.AiNotConfiguredException;
import com.secureportal.quiz.Question;
import com.secureportal.quiz.QuestionGenerationException;
import com.secureportal.quiz.QuestionGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Does the actual work of a job, wherever it is called from (a queue consumer or a local thread). Progress is
 * written to the job row as each batch of questions is saved, so a page that is polling sees it move, and questions
 * that were made before a failure are kept.
 */
@Component
public class GenerationJobRunner {

    private static final Logger log = LoggerFactory.getLogger(GenerationJobRunner.class);

    private final GenerationJobRepository jobRepository;
    private final QuestionGenerationService generationService;

    public GenerationJobRunner(GenerationJobRepository jobRepository, QuestionGenerationService generationService) {
        this.jobRepository = jobRepository;
        this.generationService = generationService;
    }

    public void run(UUID jobId) {
        GenerationJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || !job.isOpen()) {
            // Deleted with its course, or already handled (a queue can deliver the same message twice).
            return;
        }
        job.start();
        jobRepository.save(job);

        try {
            List<Question> made = generationService.generate(job.getLessonId(), job.getRequestedCount(), job.getDifficulty(),
                    job.isFinalOnly(), produced -> {
                        job.progress(produced);
                        jobRepository.save(job);
                    });
            String note = made.size() < job.getRequestedCount()
                    ? "Made " + made.size() + " of " + job.getRequestedCount() + "; the lecture didn't have enough new material for the rest."
                    : null;
            job.finish(made.size(), note);
        } catch (AiException | AiNotConfiguredException | QuestionGenerationException e) {
            log.warn("Generation job {} failed", jobId, e);
            job.fail(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Generation job {} crashed", jobId, e);
            job.fail("Something went wrong while generating. Try again.");
        }
        jobRepository.save(job);
    }
}
