package com.secureportal.api;

import com.secureportal.api.dto.ContentItemDto;
import com.secureportal.audit.AuditService;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.content.ContentItem;
import com.secureportal.content.ContentNotFoundException;
import com.secureportal.content.ContentRepository;
import com.secureportal.content.ContentService;
import com.secureportal.content.ContentType;
import com.secureportal.content.dto.EditForm;
import com.secureportal.content.dto.UploadForm;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * URL-level access is already enforced by SecurityConfig ({@code /api/admin/**}
 * requires ROLE_ADMIN). The {@code @PreAuthorize} here is a second,
 * independent check at the method level — defence in depth.
 */
@RestController
@RequestMapping("/api/admin/content")
@PreAuthorize("hasRole('ADMIN')")
public class AdminContentApiController {

    private final ContentRepository contentRepository;
    private final ContentService contentService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public AdminContentApiController(ContentRepository contentRepository, ContentService contentService,
                                      UserRepository userRepository, AuditService auditService) {
        this.contentRepository = contentRepository;
        this.contentService = contentService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public List<ContentItemDto> list() {
        return contentRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(ContentItemDto::from)
                .toList();
    }

    @PostMapping
    public ContentItemDto create(@Valid @ModelAttribute UploadForm form,
                                  @AuthenticationPrincipal AppPrincipal principal) {
        User uploadedBy = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new IllegalStateException("Signed-in user not found: " + principal.getUserId()));

        ContentItem created = contentService.create(form, uploadedBy);
        auditService.log(principal.getEmail(), "UPLOAD", created.getId(),
                created.getContentType() + " \"" + created.getTitle() + "\"");
        return ContentItemDto.from(created);
    }

    @PutMapping("/{id}")
    public ContentItemDto update(@PathVariable UUID id, @Valid @RequestBody EditForm form,
                                  @AuthenticationPrincipal AppPrincipal principal) {
        ContentItem updated = contentService.update(id, form);
        auditService.log(principal.getEmail(), "EDIT", updated.getId(), "\"" + updated.getTitle() + "\"");
        return ContentItemDto.from(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal) {
        ContentItem item = contentRepository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException(id));
        String title = item.getTitle();
        ContentType type = item.getContentType();

        contentService.delete(id);
        auditService.log(principal.getEmail(), "DELETE", id, type + " \"" + title + "\"");
    }
}
