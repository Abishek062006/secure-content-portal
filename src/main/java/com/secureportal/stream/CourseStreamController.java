package com.secureportal.stream;

import com.secureportal.course.Lesson;
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

    public CourseStreamController(TicketGuard ticketGuard, LessonRepository lessonRepository,
                                  RangedMediaResponder mediaResponder) {
        this.ticketGuard = ticketGuard;
        this.lessonRepository = lessonRepository;
        this.mediaResponder = mediaResponder;
    }

    @GetMapping("/api/course-stream/{ticket}")
    public ResponseEntity<InputStreamResource> stream(@PathVariable String ticket, HttpServletRequest request) {
        StreamTicket streamTicket = ticketGuard.verify(ticket, request, StreamTicket.Purpose.COURSE_VIDEO);

        Lesson lesson = lessonRepository.findById(streamTicket.contentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        return mediaResponder.respond(lesson.getVideoKey(), lesson.getVideoMime(), request);
    }
}
