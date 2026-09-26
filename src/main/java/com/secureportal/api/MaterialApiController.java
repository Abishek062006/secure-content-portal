package com.secureportal.api;

import com.secureportal.api.dto.MaterialDto;
import com.secureportal.assessment.AssessmentAccess;
import com.secureportal.assessment.AssessmentAccessException;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.Course;
import com.secureportal.course.CourseMaterial;
import com.secureportal.course.EnrollmentRequiredException;
import com.secureportal.course.LearningService;
import com.secureportal.course.MaterialKind;
import com.secureportal.course.MaterialService;
import com.secureportal.storage.StorageObject;
import com.secureportal.storage.StorageService;
import com.secureportal.stream.StreamTicket;
import com.secureportal.stream.StreamTicketService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/** A learner opening or downloading a module's study material, with the same access rules as its lessons. */
@RestController
public class MaterialApiController {

    private final MaterialService materialService;
    private final LearningService learningService;
    private final AssessmentAccess assessmentAccess;
    private final StreamTicketService ticketService;
    private final StorageService storageService;

    public MaterialApiController(MaterialService materialService, LearningService learningService,
                                 AssessmentAccess assessmentAccess, StreamTicketService ticketService,
                                 StorageService storageService) {
        this.materialService = materialService;
        this.learningService = learningService;
        this.assessmentAccess = assessmentAccess;
        this.ticketService = ticketService;
        this.storageService = storageService;
    }

    public record MaterialView(MaterialDto material, String courseTitle, String ticket) {
    }

    @GetMapping("/api/courses/{courseId}/materials/{id}")
    public MaterialView view(@PathVariable UUID courseId, @PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal,
                             HttpServletRequest request) {
        CourseMaterial material = authorize(courseId, id, principal);
        Course course = learningService.visibleCourse(courseId, principal.isAdmin());
        String ticket = null;
        if (material.getKind() != MaterialKind.LINK && material.getKind() != MaterialKind.DOCUMENT) {
            ticket = ticketService.mint(material.getId(), principal.getUserId(), request, StreamTicket.Purpose.MATERIAL,
                    Duration.ofMinutes(30));
        }
        return new MaterialView(MaterialDto.of(material), course.getTitle(), ticket);
    }

    /** Only when the admin allowed it; otherwise the file is viewable inside the portal but never handed over. */
    @GetMapping("/api/courses/{courseId}/materials/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID courseId, @PathVariable UUID id,
                                                        @AuthenticationPrincipal AppPrincipal principal) {
        CourseMaterial material = authorize(courseId, id, principal);
        if (!material.isDownloadable() || material.getStorageKey() == null) {
            throw new com.secureportal.course.MaterialDownloadNotAllowedException();
        }
        StorageObject object = storageService.get(material.getStorageKey(), null, null);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(material.getMime() == null ? "application/octet-stream" : material.getMime()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(material.getFilename() == null ? "material" : material.getFilename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(object.content()));
    }

    private CourseMaterial authorize(UUID courseId, UUID id, AppPrincipal principal) {
        learningService.visibleCourse(courseId, principal.isAdmin());
        CourseMaterial material = materialService.find(id);
        if (!material.getCourseId().equals(courseId)) {
            throw new com.secureportal.course.MaterialNotFoundException();
        }
        if (!principal.isAdmin()) {
            if (learningService.enrollment(principal.getUserId(), courseId).isEmpty()) {
                throw new EnrollmentRequiredException();
            }
            String locked = assessmentAccess.lockedModules(courseId, principal.getUserId()).get(material.getModuleId());
            if (locked != null) {
                throw new AssessmentAccessException(locked);
            }
        }
        return material;
    }
}
