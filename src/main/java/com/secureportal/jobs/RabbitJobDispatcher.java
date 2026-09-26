package com.secureportal.jobs;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

/** Publishes the job id; the row in MySQL carries everything else, so the message stays tiny. */
public class RabbitJobDispatcher implements JobDispatcher {

    private final RabbitTemplate template;

    public RabbitJobDispatcher(RabbitTemplate template) {
        this.template = template;
    }

    @Override
    public void dispatch(UUID jobId) {
        template.convertAndSend(QueueConfig.GENERATION_QUEUE, jobId.toString());
    }
}
