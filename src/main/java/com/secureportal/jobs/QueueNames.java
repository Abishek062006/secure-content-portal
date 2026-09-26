package com.secureportal.jobs;

/**
 * The RabbitMQ queue names, with an optional prefix ({@code app.queue.name-prefix}) so that two copies of the app
 * (say the one you run and a test run) sharing one broker never consume each other's jobs.
 */
public class QueueNames {

    private final String prefix;

    public QueueNames(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }

    public String getGeneration() {
        return prefix + "question-generation";
    }

    public String getDeadLetter() {
        return prefix + "question-generation.failed";
    }

    public String getTranscode() {
        return prefix + "video-transcoding";
    }
}
