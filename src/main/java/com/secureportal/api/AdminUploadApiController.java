package com.secureportal.api;

import com.secureportal.api.dto.CourseOutlineDto.LessonDto;
import com.secureportal.audit.AuditService;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.Lesson;
import com.secureportal.course.CourseStructureService;
import com.secureportal.video.DirectUploadService;
import com.secureportal.video.TranscodeRequests;
import com.secureportal.video.VideoUpload;
import com.secureportal.video.VideoUploadException;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Direct-to-bucket video uploads: start, get signed URLs for parts, list what arrived, complete or abort.
 * Only available when storage is S3-compatible; otherwise the page falls back to uploading through the server.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUploadApiController {

    private final ObjectProvider<DirectUploadService> uploads;
    private final CourseOutlineAssembler assembler;
    private final CourseStructureService structureService;
    private final TranscodeRequests transcodeRequests;
    private final AuditService auditService;

    public AdminUploadApiController(ObjectProvider<DirectUploadService> uploads, CourseOutlineAssembler assembler,
                                    CourseStructureService structureService, TranscodeRequests transcodeRequests,
                                    AuditService auditService) {
        this.uploads = uploads;
        this.assembler = assembler;
        this.structureService = structureService;
        this.transcodeRequests = transcodeRequests;
        this.auditService = auditService;
    }

    public record UploadConfig(boolean direct, long partSizeBytes, long maxBytes) {
    }

    public record StartRequest(@NotNull String title, String description, @NotNull String filename, long sizeBytes) {
    }

    public record StartResponse(UUID id, long partSizeBytes, int partCount) {
    }

    public record PartsRequest(@NotNull List<Integer> partNumbers) {
    }

    public record CompleteRequest(@NotNull List<DirectUploadService.Part> parts) {
    }

    @GetMapping("/uploads/config")
    public UploadConfig config() {
        return new UploadConfig(uploads.getIfAvailable() != null, DirectUploadService.PART_SIZE,
                com.secureportal.content.ContentType.VIDEO.getMaxSizeBytes());
    }

    @PostMapping("/modules/{moduleId}/uploads")
    public StartResponse start(@PathVariable UUID moduleId, @RequestBody StartRequest request,
                               @AuthenticationPrincipal AppPrincipal principal) {
        VideoUpload upload = service().start(moduleId, request.title(), request.description(), request.filename(),
                request.sizeBytes(), principal.getUserId());
        return new StartResponse(upload.getId(), upload.getPartSize(), upload.partCount());
    }

    @PostMapping("/uploads/{id}/parts")
    public List<DirectUploadService.PartUrl> parts(@PathVariable UUID id, @RequestBody PartsRequest request) {
        return service().presign(id, request.partNumbers());
    }

    @GetMapping("/uploads/{id}/parts")
    public List<DirectUploadService.Part> uploaded(@PathVariable UUID id) {
        return service().uploaded(id);
    }

    @PostMapping("/uploads/{id}/complete")
    public LessonDto complete(@PathVariable UUID id, @RequestBody CompleteRequest request,
                              @AuthenticationPrincipal AppPrincipal principal) {
        Lesson lesson = service().complete(id, request.parts());
        auditService.log(principal.getEmail(), "LESSON_ADD", lesson.getCourseId(), "\"" + lesson.getTitle() + "\" (direct upload)");
        transcodeRequests.request(lesson.getId());
        return assembler.lessonDto(structureService.findLesson(lesson.getId()), null, true);
    }

    @DeleteMapping("/uploads/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abort(@PathVariable UUID id) {
        service().abort(id);
    }

    private DirectUploadService service() {
        DirectUploadService service = uploads.getIfAvailable();
        if (service == null) {
            throw new VideoUploadException("Direct uploads need S3-compatible storage; upload through the server instead.");
        }
        return service;
    }
}
