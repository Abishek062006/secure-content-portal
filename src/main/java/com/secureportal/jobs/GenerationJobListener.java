package com.secureportal.jobs;

import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.util.UUID;

/** Consumes generation jobs from RabbitMQ, two at a time at most, and runs them. */
public class GenerationJobListener {

    private final GenerationJobRunner runner;

    public GenerationJobListener(GenerationJobRunner runner) {
        this.runner = runner;
    }

    @RabbitListener(queues = QueueConfig.GENERATION_QUEUE, concurrency = "1-2")
    public void onMessage(String jobId) {
        runner.run(UUID.fromString(jobId));
    }
}
