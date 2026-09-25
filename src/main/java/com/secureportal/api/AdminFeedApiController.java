package com.secureportal.api;

import com.secureportal.api.dto.PostDto;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.feed.FeedService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** URL-level access is enforced by SecurityConfig; {@code @PreAuthorize} is the independent method-level second check. */
@RestController
@RequestMapping("/api/admin/posts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFeedApiController {

    private final FeedService feedService;
    private final PostAssembler assembler;

    public AdminFeedApiController(FeedService feedService, PostAssembler assembler) {
        this.feedService = feedService;
        this.assembler = assembler;
    }

    public record PostRequest(String body, UUID courseId, boolean pinned, Instant publishAt) {
    }

    @GetMapping("/scheduled")
    public List<PostDto> scheduled(@AuthenticationPrincipal AppPrincipal principal) {
        return assembler.posts(feedService.scheduled(), principal.getUserId());
    }

    @PostMapping(consumes = "multipart/form-data")
    public PostDto create(@RequestParam String body,
                          @RequestParam(required = false) UUID courseId,
                          @RequestParam(defaultValue = "false") boolean pinned,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant publishAt,
                          @RequestParam(required = false) MultipartFile image,
                          @AuthenticationPrincipal AppPrincipal principal) {
        return assembler.post(feedService.create(principal.getUserId(), body, courseId, pinned, publishAt, image),
                principal.getUserId());
    }

    @PutMapping("/{id}")
    public PostDto edit(@PathVariable UUID id, @RequestBody PostRequest request, @AuthenticationPrincipal AppPrincipal principal) {
        return assembler.post(feedService.edit(id, request.body(), request.courseId(), request.pinned(), request.publishAt()),
                principal.getUserId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        feedService.delete(id);
    }
}
