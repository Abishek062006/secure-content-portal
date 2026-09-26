package com.secureportal.video;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

public class RabbitTranscodeDispatcher implements TranscodeDispatcher {

    private final RabbitTemplate template;
    private final com.secureportal.jobs.QueueNames names;

    public RabbitTranscodeDispatcher(RabbitTemplate template, com.secureportal.jobs.QueueNames names) {
        this.template = template;
        this.names = names;
    }

    @Override
    public void dispatch(UUID lessonId) {
        template.convertAndSend(names.getTranscode(), lessonId.toString());
    }
}
