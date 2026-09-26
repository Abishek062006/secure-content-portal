package com.secureportal.jobs;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The RabbitMQ side, only present when {@code app.queue.enabled=true}. */
@Configuration
@EnableRabbit
@ConditionalOnProperty(name = "app.queue.enabled", havingValue = "true")
public class QueueConfig {

    @Bean
    QueueNames queueNames(@org.springframework.beans.factory.annotation.Value("${app.queue.name-prefix:}") String prefix) {
        return new QueueNames(prefix);
    }

    /** Messages a consumer rejects land in the dead-letter queue, where they can be inspected instead of looping. */
    @Bean
    Queue generationQueue(QueueNames names) {
        return QueueBuilder.durable(names.getGeneration())
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", names.getDeadLetter())
                .build();
    }

    @Bean
    Queue deadLetterQueue(QueueNames names) {
        return QueueBuilder.durable(names.getDeadLetter()).build();
    }

    @Bean
    Queue transcodeQueue(QueueNames names) {
        return QueueBuilder.durable(names.getTranscode()).build();
    }

    @Bean
    com.secureportal.video.RabbitTranscodeDispatcher rabbitTranscodeDispatcher(RabbitTemplate template, QueueNames names) {
        return new com.secureportal.video.RabbitTranscodeDispatcher(template, names);
    }

    @Bean
    com.secureportal.video.TranscodeListener transcodeListener(com.secureportal.video.TranscodeService service) {
        return new com.secureportal.video.TranscodeListener(service);
    }

    @Bean
    RabbitJobDispatcher rabbitJobDispatcher(RabbitTemplate template, QueueNames names) {
        return new RabbitJobDispatcher(template, names);
    }

    @Bean
    GenerationJobListener generationJobListener(GenerationJobRunner runner) {
        return new GenerationJobListener(runner);
    }
}
