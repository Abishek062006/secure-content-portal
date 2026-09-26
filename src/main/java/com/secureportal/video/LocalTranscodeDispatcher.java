package com.secureportal.video;

import com.secureportal.course.HlsStatus;
import com.secureportal.course.Lesson;
import com.secureportal.course.LessonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One video at a time on a background thread; this is what runs without RabbitMQ. */
@Component
@ConditionalOnProperty(name = "app.queue.enabled", havingValue = "false", matchIfMissing = true)
public class LocalTranscodeDispatcher implements TranscodeDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LocalTranscodeDispatcher.class);

    private final TranscodeService transcodeService;
    private final LessonRepository lessonRepository;
    private final ExecutorService executor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(200),
            runnable -> {
                Thread thread = new Thread(runnable, "transcode");
                thread.setDaemon(true);
                return thread;
            });

    public LocalTranscodeDispatcher(TranscodeService transcodeService, LessonRepository lessonRepository) {
        this.transcodeService = transcodeService;
        this.lessonRepository = lessonRepository;
    }

    @Override
    public void dispatch(UUID lessonId) {
        executor.execute(() -> transcodeService.transcode(lessonId));
    }

    /** A video caught mid-transcode by a restart can't be resumed here; it can be retried from the course editor. */
    @EventListener(ApplicationReadyEvent.class)
    void failInterrupted() {
        List<Lesson> stuck = lessonRepository.findByHlsStatus(HlsStatus.PROCESSING);
        for (Lesson lesson : stuck) {
            log.warn("Marking lesson {} as failed: transcoding was interrupted by a restart", lesson.getId());
            lesson.setHls(HlsStatus.FAILED, "Processing was interrupted by a restart. Retry it from the editor.");
        }
        lessonRepository.saveAll(stuck);
    }
}
