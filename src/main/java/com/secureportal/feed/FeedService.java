package com.secureportal.feed;

import com.secureportal.common.FileValidator;
import com.secureportal.common.ValidatedFile;
import com.secureportal.course.Course;
import com.secureportal.course.CourseRepository;
import com.secureportal.course.CourseStatus;
import com.secureportal.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Posts, reactions and comments. Learners only ever see posts whose publish time has passed. */
@Service
public class FeedService {

    public static final int PAGE_SIZE = 10;
    static final int MAX_BODY = 3000;
    static final int MAX_COMMENT = 1000;
    static final long MAX_VIDEO_BYTES = 500L * 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(FeedService.class);

    private final PostRepository postRepository;
    private final PostReactionRepository reactionRepository;
    private final PostCommentRepository commentRepository;
    private final CourseRepository courseRepository;
    private final FileValidator fileValidator;
    private final StorageService storageService;

    public FeedService(PostRepository postRepository, PostReactionRepository reactionRepository,
                       PostCommentRepository commentRepository, CourseRepository courseRepository,
                       FileValidator fileValidator, StorageService storageService) {
        this.postRepository = postRepository;
        this.reactionRepository = reactionRepository;
        this.commentRepository = commentRepository;
        this.courseRepository = courseRepository;
        this.fileValidator = fileValidator;
        this.storageService = storageService;
    }

    public Page<Post> published(int page) {
        return postRepository.findPublished(Instant.now(), PageRequest.of(Math.max(page, 0), PAGE_SIZE));
    }

    public List<Post> scheduled() {
        return postRepository.findScheduled(Instant.now());
    }

    /** Scheduled posts don't exist as far as learners are concerned; admins may preview them. */
    public Post visible(UUID id, boolean admin) {
        return postRepository.findById(id)
                .filter(p -> admin || p.isPublished())
                .orElseThrow(PostNotFoundException::new);
    }

    @Transactional
    public Post create(Long authorId, String body, UUID courseId, boolean pinned, Instant publishAt, MultipartFile image) {
        return create(authorId, body, courseId, pinned, publishAt, image, null);
    }

    @Transactional
    public Post create(Long authorId, String body, UUID courseId, boolean pinned, Instant publishAt, MultipartFile image,
                       MultipartFile video) {
        String text = cleanBody(body);
        checkCourse(courseId);
        boolean hasImage = image != null && !image.isEmpty();
        boolean hasVideo = video != null && !video.isEmpty();
        if (hasImage && hasVideo) {
            throw new InvalidPostException("Attach an image or a video, not both.");
        }
        ValidatedFile validatedImage = hasImage ? fileValidator.validateThumbnail(image) : null;
        ValidatedFile validatedVideo = null;
        if (hasVideo) {
            if (video.getSize() > MAX_VIDEO_BYTES) {
                throw new InvalidPostException("A post video can be at most 500 MB. Longer videos belong in a course.");
            }
            validatedVideo = fileValidator.validate(video, com.secureportal.content.ContentType.VIDEO);
        }
        Post post = new Post(authorId, text, courseId, pinned, publishAt);
        String key = null;
        if (validatedImage != null) {
            key = store(post, "image", image, validatedImage);
            post.setImage(key, validatedImage.detectedMimeType());
        } else if (validatedVideo != null) {
            key = store(post, "video", video, validatedVideo);
            post.setVideo(key, validatedVideo.detectedMimeType());
        }
        try {
            return postRepository.save(post);
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
    }

    private String store(Post post, String kind, MultipartFile file, ValidatedFile validated) {
        String key = "posts/" + post.getId() + "/" + kind + "/" + validated.originalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
        try (InputStream in = file.getInputStream()) {
            storageService.put(key, in, validated.sizeBytes(), validated.detectedMimeType());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the uploaded " + kind, e);
        }
        return key;
    }

    @Transactional
    public Post edit(UUID id, String body, UUID courseId, boolean pinned, Instant publishAt) {
        Post post = postRepository.findById(id).orElseThrow(PostNotFoundException::new);
        checkCourse(courseId);
        post.edit(cleanBody(body), courseId, pinned, publishAt);
        return postRepository.save(post);
    }

    @Transactional
    public void delete(UUID id) {
        Post post = postRepository.findById(id).orElseThrow(PostNotFoundException::new);
        String imageKey = post.getImageKey();
        String videoKey = post.getVideoKey();
        postRepository.delete(post);
        deleteQuietly(imageKey);
        deleteQuietly(videoKey);
    }

    @Transactional
    public void react(UUID postId, Long userId, ReactionType type, boolean admin) {
        visible(postId, admin);
        PostReaction reaction = reactionRepository.findByPostIdAndUserId(postId, userId)
                .orElseGet(() -> new PostReaction(postId, userId, type));
        reaction.setType(type);
        reactionRepository.save(reaction);
    }

    @Transactional
    public void unreact(UUID postId, Long userId) {
        reactionRepository.findByPostIdAndUserId(postId, userId).ifPresent(reactionRepository::delete);
    }

    public List<PostComment> comments(UUID postId, boolean admin) {
        visible(postId, admin);
        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId);
    }

    @Transactional
    public PostComment comment(UUID postId, Long userId, String body, boolean admin) {
        visible(postId, admin);
        String text = body == null ? "" : body.trim();
        if (text.isEmpty() || text.length() > MAX_COMMENT) {
            throw new InvalidPostException("A comment must be between 1 and " + MAX_COMMENT + " characters.");
        }
        return commentRepository.save(new PostComment(postId, userId, text));
    }

    /** Authors can remove their own comments; admins can remove anyone's (moderation). */
    @Transactional
    public void deleteComment(UUID commentId, Long userId, boolean admin) {
        PostComment comment = commentRepository.findById(commentId).orElseThrow(PostNotFoundException::new);
        if (!admin && !comment.getUserId().equals(userId)) {
            throw new PostNotFoundException();
        }
        commentRepository.delete(comment);
    }

    private String cleanBody(String body) {
        String text = body == null ? "" : body.trim();
        if (text.isEmpty() || text.length() > MAX_BODY) {
            throw new InvalidPostException("A post needs text of 1 to " + MAX_BODY + " characters.");
        }
        return text;
    }

    private void checkCourse(UUID courseId) {
        if (courseId == null) {
            return;
        }
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new InvalidPostException("That course doesn't exist."));
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new InvalidPostException("Only published courses can be promoted.");
        }
    }

    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            storageService.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete storage object {}", key, e);
        }
    }
}
