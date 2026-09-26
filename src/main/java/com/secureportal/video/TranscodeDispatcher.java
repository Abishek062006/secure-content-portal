package com.secureportal.video;

import java.util.UUID;

/** Runs a lesson's transcoding somewhere: a RabbitMQ queue when it's on, a single local thread otherwise. */
public interface TranscodeDispatcher {

    void dispatch(UUID lessonId);
}
