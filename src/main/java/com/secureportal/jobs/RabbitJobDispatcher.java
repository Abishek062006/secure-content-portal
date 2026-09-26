package com.secureportal.jobs;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

/** Publishes the job id; the row in MySQL carries everything else, so the message stays tiny. */
public class RabbitJobDispatcher implements JobDispatcher {

    private final RabbitTemplate template;
    private final QueueNames names;

    public RabbitJobDispatcher(RabbitTemplate template, QueueNames names) {
        this.template = template;
        this.names = names;
    }

    @Override
    public void dispatch(UUID jobId) {
        template.convertAndSend(names.getGeneration(), jobId.toString());
    }
}
