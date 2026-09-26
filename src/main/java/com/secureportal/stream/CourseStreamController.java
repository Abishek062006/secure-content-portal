package com.secureportal.stream;

import com.secureportal.course.HlsStatus;
import com.secureportal.course.Lesson;
import com.secureportal.storage.StorageObject;
import com.secureportal.storage.StorageService;
import com.secureportal.video.Hls;
import com.secureportal.course.LessonRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Same ticket-gated, range-capable delivery as {@link StreamController}, for a lesson's video. */
@RestController
public class CourseStreamController {

    private final TicketGuard ticketGuard;
    private final LessonRepository lessonRepository;
    private final RangedMediaResponder mediaResponder;
    private final StorageService storageService;

    public CourseStreamController(TicketGuard ticketGuard, LessonRepository lessonRepository,
                                  RangedMediaResponder mediaResponder, StorageService storageService) {
        this.ticketGuard = ticketGuard;
        this.lessonRepository = lessonRepository;
        this.mediaResponder = mediaResponder;
        this.storageService = storageService;
    }

    @GetMapping("/api/course-stream/{ticket}")
    public ResponseEntity<InputStreamResource> stream(@PathVariable String ticket, HttpServletRequest request) {
        StreamTicket streamTicket = ticketGuard.verify(ticket, request, StreamTicket.Purpose.COURSE_VIDEO);

        Lesson lesson = lessonRepository.findById(streamTicket.contentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        return mediaResponder.respond(lesson.getVideoKey(), lesson.getVideoMime(), request);
    }

    /**
     * The adaptive-streaming files of a lesson: {@code master.m3u8}, then each rendition's playlist and segments.
     * The playlists use relative addresses, so the player keeps requesting under this same ticketed path and every
     * segment is checked like the video itself. Only the file names this app writes are served.
     */
    @GetMapping("/api/course-stream/{ticket}/hls/**")
    public ResponseEntity<?> hls(@PathVariable String ticket, HttpServletRequest request) {
        StreamTicket streamTicket = ticketGuard.verify(ticket, request, StreamTicket.Purpose.COURSE_VIDEO);
        String uri = request.getRequestURI();
        String path = uri.substring(uri.indexOf("/hls/") + "/hls/".length());
        if (!Hls.isSafePath(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Lesson lesson = lessonRepository.findById(streamTicket.contentId())
                .filter(l -> l.getHlsStatus() == HlsStatus.READY)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String key = lesson.hlsPrefix() + path;

        if (path.endsWith(".m3u8")) {
            try (StorageObject object = storageService.get(key, null, null)) {
                return ResponseEntity.ok()
                        .header("Content-Type", Hls.contentType(path))
                        .header("Cache-Control", "no-store")
                        .header("X-Content-Type-Options", "nosniff")
                        .body(object.content().readAllBytes());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Could not read " + key, e);
            }
        }
        return mediaResponder.respond(key, Hls.contentType(path), request);
    }
}
