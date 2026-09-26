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

    public static final String GENERATION_QUEUE = "question-generation";
    public static final String DEAD_LETTER_QUEUE = "question-generation.failed";

    /** Messages a consumer rejects land in the dead-letter queue, where they can be inspected instead of looping. */
    @Bean
    Queue generationQueue() {
        return QueueBuilder.durable(GENERATION_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    RabbitJobDispatcher rabbitJobDispatcher(RabbitTemplate template) {
        return new RabbitJobDispatcher(template);
    }

    @Bean
    GenerationJobListener generationJobListener(GenerationJobRunner runner) {
        return new GenerationJobListener(runner);
    }
}
