package com.secureportal.api;

import com.secureportal.api.dto.MaterialDto;
import com.secureportal.audit.AuditService;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.CourseMaterial;
import com.secureportal.course.MaterialService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** URL-level access is enforced by SecurityConfig; {@code @PreAuthorize} is the independent method-level second check. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMaterialApiController {

    private final MaterialService materialService;
    private final AuditService auditService;

    public AdminMaterialApiController(MaterialService materialService, AuditService auditService) {
        this.materialService = materialService;
        this.auditService = auditService;
    }

    public record MaterialRequest(String title, String description, boolean downloadable, String url) {
    }

    @PostMapping(value = "/modules/{moduleId}/materials", consumes = "multipart/form-data")
    public MaterialDto add(@PathVariable UUID moduleId,
                           @RequestParam String title,
                           @RequestParam(required = false) String description,
                           @RequestParam(required = false) String url,
                           @RequestParam(defaultValue = "false") boolean downloadable,
                           @RequestParam(required = false) MultipartFile file,
                           @AuthenticationPrincipal AppPrincipal principal) {
        CourseMaterial created = materialService.add(moduleId, title, description, url, downloadable, file);
        auditService.log(principal.getEmail(), "MATERIAL_ADD", created.getCourseId(),
                created.getKind() + " \"" + created.getTitle() + "\"" + (created.isDownloadable() ? " (downloadable)" : ""));
        return MaterialDto.of(created);
    }

    @PutMapping("/materials/{id}")
    public MaterialDto update(@PathVariable UUID id, @RequestBody MaterialRequest request,
                              @AuthenticationPrincipal AppPrincipal principal) {
        CourseMaterial updated = materialService.update(id, request.title(), request.description(), request.downloadable(), request.url());
        auditService.log(principal.getEmail(), "MATERIAL_EDIT", updated.getCourseId(), "\"" + updated.getTitle() + "\"");
        return MaterialDto.of(updated);
    }

    @DeleteMapping("/materials/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal) {
        CourseMaterial material = materialService.find(id);
        materialService.delete(id);
        auditService.log(principal.getEmail(), "MATERIAL_DELETE", material.getCourseId(), "\"" + material.getTitle() + "\"");
    }
}
