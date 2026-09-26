package com.secureportal.video;

import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.util.UUID;

/** One transcode at a time per app instance: it is CPU heavy and long. */
public class TranscodeListener {

    private final TranscodeService transcodeService;

    public TranscodeListener(TranscodeService transcodeService) {
        this.transcodeService = transcodeService;
    }

    @RabbitListener(queues = "#{queueNames.transcode}", concurrency = "1")
    public void onMessage(String lessonId) {
        transcodeService.transcode(UUID.fromString(lessonId));
    }
}
