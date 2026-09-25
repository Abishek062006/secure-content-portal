package com.secureportal.api;

import com.secureportal.api.dto.CourseDetailResponse;
import com.secureportal.api.dto.CourseDto;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.Course;
import com.secureportal.course.CourseRepository;
import com.secureportal.course.CourseService;
import com.secureportal.stream.StreamTicket;
import com.secureportal.stream.StreamTicketService;
import com.secureportal.storage.StorageObject;
import com.secureportal.storage.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** The learner-facing side of courses: the catalog, cover images, and a per-course video ticket. */
@RestController
@RequestMapping("/api/courses")
public class CourseApiController {

    private final CourseRepository courseRepository;
    private final CourseService courseService;
    private final StreamTicketService ticketService;
    private final StorageService storageService;

    public CourseApiController(CourseRepository courseRepository, CourseService courseService,
                               StreamTicketService ticketService, StorageService storageService) {
        this.courseRepository = courseRepository;
        this.courseService = courseService;
        this.ticketService = ticketService;
        this.storageService = storageService;
    }

    @GetMapping
    public List<CourseDto> list() {
        return courseRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(CourseDto::forViewer)
                .toList();
    }

    @GetMapping("/{id}")
    public CourseDetailResponse detail(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal,
                                       HttpServletRequest request) {
        Course course = courseService.find(id);
        courseService.recordView(id);

        String ticket = ticketService.mint(id, principal.getUserId(), request,
                StreamTicket.Purpose.COURSE_VIDEO, Duration.ofMinutes(30));
        return new CourseDetailResponse(CourseDto.forViewer(course), ticket, courseService.transcript(course));
    }

    /**
     * Cover images sit behind sign-in like everything else, but aren't
     * ticketed: they're catalog art, and a per-image ticket on a page of
     * dozens of cards would cost more than it protects.
     */
    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<byte[]> thumbnail(@PathVariable UUID id) {
        Course course = courseService.find(id);
        if (course.getThumbnailKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        try (StorageObject object = storageService.get(course.getThumbnailKey(), null, null)) {
            byte[] bytes = object.content().readAllBytes();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(course.getThumbnailMime()))
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read thumbnail for course " + id, e);
        }
    }
}
