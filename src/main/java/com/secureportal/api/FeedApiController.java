package com.secureportal.api;

import com.secureportal.api.dto.PostDto;
import com.secureportal.api.dto.PostDto.CommentDto;
import com.secureportal.api.dto.PostDto.FeedPage;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.feed.FeedService;
import com.secureportal.feed.Post;
import com.secureportal.feed.PostComment;
import com.secureportal.feed.ReactionType;
import com.secureportal.storage.StorageObject;
import com.secureportal.storage.StorageService;
import com.secureportal.user.UserRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** The viewer's side of the feed: read posts, react, comment. */
@RestController
public class FeedApiController {

    private final FeedService feedService;
    private final PostAssembler assembler;
    private final StorageService storageService;
    private final UserRepository userRepository;

    public FeedApiController(FeedService feedService, PostAssembler assembler, StorageService storageService,
                             UserRepository userRepository) {
        this.feedService = feedService;
        this.assembler = assembler;
        this.storageService = storageService;
        this.userRepository = userRepository;
    }

    public record ReactionRequest(@NotNull ReactionType type) {
    }

    public record CommentRequest(String body) {
    }

    @GetMapping("/api/feed")
    public FeedPage feed(@RequestParam(defaultValue = "0") int page, @AuthenticationPrincipal AppPrincipal principal) {
        Page<Post> posts = feedService.published(page);
        return new FeedPage(assembler.posts(posts.getContent(), principal.getUserId()), posts.hasNext());
    }

    /** Any member can post; admins get extras (pinning, scheduling, course promos) on /api/admin/posts. */
    @PostMapping(value = "/api/posts", consumes = "multipart/form-data")
    public PostDto create(@RequestParam String body,
                          @RequestParam(required = false) String title,
                          @RequestParam(defaultValue = "false") boolean article,
                          @RequestParam(required = false) UUID certificateId,
                          @RequestParam(required = false) org.springframework.web.multipart.MultipartFile image,
                          @RequestParam(required = false) org.springframework.web.multipart.MultipartFile video,
                          @AuthenticationPrincipal AppPrincipal principal) {
        Post post = feedService.createByMember(principal.getUserId(), article, title, body, certificateId, image, video);
        return assembler.post(post, principal.getUserId());
    }

    @DeleteMapping("/api/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal) {
        feedService.deleteAs(id, principal.getUserId(), principal.isAdmin());
    }

    @GetMapping("/api/profiles/{userId}/posts")
    public FeedPage postsBy(@PathVariable Long userId, @RequestParam(defaultValue = "0") int page,
                            @AuthenticationPrincipal AppPrincipal principal) {
        Page<Post> posts = feedService.publishedBy(userId, page);
        return new FeedPage(assembler.posts(posts.getContent(), principal.getUserId()), posts.hasNext());
    }

    @GetMapping("/api/posts/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal) {
        Post post = feedService.visible(id, principal.isAdmin());
        if (post.getImageKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        try (StorageObject object = storageService.get(post.getImageKey(), null, null)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(post.getImageMime()))
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(object.content().readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read image for post " + id, e);
        }
    }

    @PutMapping("/api/posts/{id}/reaction")
    public PostDto react(@PathVariable UUID id, @RequestBody ReactionRequest request,
                         @AuthenticationPrincipal AppPrincipal principal) {
        if (request == null || request.type() == null) {
            throw new com.secureportal.feed.InvalidPostException("Choose a reaction.");
        }
        feedService.react(id, principal.getUserId(), request.type(), principal.isAdmin());
        return assembler.post(feedService.visible(id, principal.isAdmin()), principal.getUserId());
    }

    @DeleteMapping("/api/posts/{id}/reaction")
    public PostDto unreact(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal) {
        Post post = feedService.visible(id, principal.isAdmin());
        feedService.unreact(id, principal.getUserId());
        return assembler.post(post, principal.getUserId());
    }

    @GetMapping("/api/posts/{id}/comments")
    public List<CommentDto> comments(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal) {
        return assembler.comments(feedService.comments(id, principal.isAdmin()), principal.getUserId(), principal.isAdmin());
    }

    @PostMapping("/api/posts/{id}/comments")
    public CommentDto comment(@PathVariable UUID id, @RequestBody CommentRequest request,
                              @AuthenticationPrincipal AppPrincipal principal) {
        PostComment comment = feedService.comment(id, principal.getUserId(), request == null ? null : request.body(),
                principal.isAdmin());
        return assembler.comment(comment, userRepository.findById(principal.getUserId()).orElse(null),
                principal.getUserId(), principal.isAdmin());
    }

    @DeleteMapping("/api/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal) {
        feedService.deleteComment(id, principal.getUserId(), principal.isAdmin());
    }
}
