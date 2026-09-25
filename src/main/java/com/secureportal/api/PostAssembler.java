package com.secureportal.api;

import com.secureportal.api.dto.PostDto;
import com.secureportal.api.dto.PostDto.CommentDto;
import com.secureportal.api.dto.PostDto.PostCourseDto;
import com.secureportal.course.Course;
import com.secureportal.course.CourseRepository;
import com.secureportal.course.CourseStatus;
import com.secureportal.course.Enrollment;
import com.secureportal.course.EnrollmentRepository;
import com.secureportal.feed.Post;
import com.secureportal.feed.PostComment;
import com.secureportal.feed.PostCommentRepository;
import com.secureportal.feed.PostReaction;
import com.secureportal.feed.PostReactionRepository;
import com.secureportal.feed.ReactionType;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Builds {@link PostDto}s for a whole page of posts with a handful of batched queries, not one set per post. */
@Component
public class PostAssembler {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PostReactionRepository reactionRepository;
    private final PostCommentRepository commentRepository;

    public PostAssembler(UserRepository userRepository, CourseRepository courseRepository,
                         EnrollmentRepository enrollmentRepository, PostReactionRepository reactionRepository,
                         PostCommentRepository commentRepository) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.reactionRepository = reactionRepository;
        this.commentRepository = commentRepository;
    }

    public List<PostDto> posts(List<Post> posts, Long viewerId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = posts.stream().map(Post::getId).toList();

        Map<Long, User> authors = userRepository.findAllById(
                posts.stream().map(Post::getAuthorId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<UUID, Course> courses = courseRepository.findAllById(
                posts.stream().map(Post::getCourseId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Course::getId, c -> c));
        Set<UUID> enrolled = new HashSet<>();
        for (Enrollment e : enrollmentRepository.findByUserId(viewerId)) {
            enrolled.add(e.getCourseId());
        }

        Map<UUID, Map<String, Long>> reactions = new HashMap<>();
        for (Object[] row : reactionRepository.countByPostAndType(ids)) {
            reactions.computeIfAbsent((UUID) row[0], k -> new HashMap<>())
                    .put(((ReactionType) row[1]).name(), ((Number) row[2]).longValue());
        }
        Map<UUID, String> mine = new HashMap<>();
        for (PostReaction r : reactionRepository.findByUserIdAndPostIdIn(viewerId, ids)) {
            mine.put(r.getPostId(), r.getType().name());
        }
        Map<UUID, Long> commentCounts = new HashMap<>();
        for (Object[] row : commentRepository.countByPost(ids)) {
            commentCounts.put((UUID) row[0], ((Number) row[1]).longValue());
        }

        return posts.stream().map(post -> {
            User author = authors.get(post.getAuthorId());
            Course course = courses.get(post.getCourseId());
            PostCourseDto courseDto = course == null || course.getStatus() != CourseStatus.PUBLISHED ? null
                    : new PostCourseDto(course.getId(), course.getTitle(), course.getDescription(), course.getCategory(),
                    course.getThumbnailKey() == null ? null
                            : "/api/courses/" + course.getId() + "/thumbnail?v=" + course.getUpdatedAt().toEpochMilli(),
                    enrolled.contains(course.getId()));
            Map<String, Long> counts = reactions.getOrDefault(post.getId(), Map.of());
            return new PostDto(post.getId(), post.getBody(),
                    post.getImageKey() == null ? null
                            : "/api/posts/" + post.getId() + "/image?v=" + post.getUpdatedAt().toEpochMilli(),
                    author == null ? "Admin" : author.getDisplayName(), author == null ? null : author.getPictureUrl(),
                    post.getPublishAt(), post.isPinned(), !post.isPublished(), courseDto, counts,
                    counts.values().stream().mapToLong(Long::longValue).sum(), mine.get(post.getId()),
                    commentCounts.getOrDefault(post.getId(), 0L));
        }).toList();
    }

    public PostDto post(Post post, Long viewerId) {
        return posts(List.of(post), viewerId).get(0);
    }

    public List<CommentDto> comments(List<PostComment> comments, Long viewerId, boolean admin) {
        Collection<Long> userIds = comments.stream().map(PostComment::getUserId).collect(Collectors.toSet());
        Map<Long, User> users = userRepository.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        return comments.stream().map(c -> comment(c, users.get(c.getUserId()), viewerId, admin)).toList();
    }

    public CommentDto comment(PostComment c, User user, Long viewerId, boolean admin) {
        return new CommentDto(c.getId(), user == null ? "Former user" : user.getDisplayName(),
                user == null ? null : user.getPictureUrl(), c.getBody(), c.getCreatedAt(),
                admin || c.getUserId().equals(viewerId));
    }
}
