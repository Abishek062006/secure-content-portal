package com.secureportal.video;

import com.secureportal.course.HlsStatus;
import com.secureportal.course.LessonRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** The one place that asks for a lesson's video to be prepared for adaptive streaming. */
@Component
public class TranscodeRequests {

    private final TranscodeProperties properties;
    private final LessonRepository lessonRepository;
    private final TranscodeDispatcher dispatcher;

    public TranscodeRequests(TranscodeProperties properties, LessonRepository lessonRepository, TranscodeDispatcher dispatcher) {
        this.properties = properties;
        this.lessonRepository = lessonRepository;
        this.dispatcher = dispatcher;
    }

    public boolean enabled() {
        return properties.isEnabled();
    }

    public void request(UUID lessonId) {
        if (!properties.isEnabled()) {
            return;
        }
        lessonRepository.findById(lessonId).ifPresent(lesson -> {
            lesson.setHls(HlsStatus.PROCESSING, null);
            lessonRepository.save(lesson);
        });
        dispatcher.dispatch(lessonId);
    }
}
