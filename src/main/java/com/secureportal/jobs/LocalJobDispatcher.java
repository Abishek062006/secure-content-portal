package com.secureportal.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Runs jobs on a small thread pool inside this app. This is what happens without RabbitMQ, so background generation
 * and its progress bar work with just MySQL. A job caught mid-run by a restart can't be resumed here, so it is failed.
 */
@Component
@ConditionalOnProperty(name = "app.queue.enabled", havingValue = "false", matchIfMissing = true)
public class LocalJobDispatcher implements JobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LocalJobDispatcher.class);

    private final GenerationJobRunner runner;
    private final GenerationJobRepository jobRepository;
    private final ExecutorService executor = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(100),
            runnable -> {
                Thread thread = new Thread(runnable, "generation-job");
                thread.setDaemon(true);
                return thread;
            });

    public LocalJobDispatcher(GenerationJobRunner runner, GenerationJobRepository jobRepository) {
        this.runner = runner;
        this.jobRepository = jobRepository;
    }

    @Override
    public void dispatch(UUID jobId) {
        executor.execute(() -> runner.run(jobId));
    }

    @EventListener(ApplicationReadyEvent.class)
    void failInterruptedJobs() {
        List<GenerationJob> open = jobRepository.findByStatusIn(List.of(JobStatus.QUEUED, JobStatus.RUNNING));
        for (GenerationJob job : open) {
            log.warn("Failing generation job {} left open by a restart", job.getId());
            job.fail("The server restarted while this was running. Start it again.");
        }
        jobRepository.saveAll(open);
    }
}
