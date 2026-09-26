package com.secureportal.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.course.CourseRepository;
import com.secureportal.user.Role;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.secureportal.testsupport.TestPrincipals.as;
import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Posting, scheduling, promo posts, reactions and comments, through the real API and database. */
@SpringBootTest(properties = {"storage.provider=local", "storage.local-path=target/feed-test-storage"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class FeedFlowTest {

    private static final String ADMIN = "feed-flow-admin@example.com";
    private static final String LEARNER = "feed-flow-learner@example.com";
    private static final String OTHER = "feed-flow-other@example.com";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R',
            0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89};

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private User admin;
    private User learner;
    private User other;

    @BeforeEach
    void users() {
        admin = user(ADMIN, "Ada Admin", Role.ADMIN);
        learner = user(LEARNER, "Lena Learner", Role.VIEWER);
        other = user(OTHER, "Otto Other", Role.VIEWER);
    }

    @AfterEach
    void cleanUp() {
        postRepository.deleteAll();
        courseRepository.deleteAll();
        for (String email : List.of(ADMIN, LEARNER, OTHER)) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
    }

    @Test
    void adminPostsAndSchedulesAndViewersSeeOnlyWhatIsPublishedWithPinnedFirst() throws Exception {
        mockMvc.perform(multipart("/api/admin/posts").param("body", "Nope").with(csrf()).with(as(learner)))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/admin/posts").param("body", "   ").with(csrf()).with(as(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/admin/posts").param("body", "x".repeat(3001)).with(csrf()).with(as(admin)))
                .andExpect(status().isBadRequest());

        String older = createPost("First post", null, false, null);
        String pinned = createPost("Pinned announcement", null, true, null);
        String scheduled = createPost("Coming soon", null, false, Instant.now().plus(2, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/feed").with(as(learner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts.length()").value(2))
                .andExpect(jsonPath("$.posts[0].id").value(pinned))
                .andExpect(jsonPath("$.posts[0].pinned").value(true))
                .andExpect(jsonPath("$.posts[0].authorName").value("Ada Admin"))
                .andExpect(jsonPath("$.posts[1].id").value(older))
                .andExpect(jsonPath("$.hasMore").value(false));

        mockMvc.perform(get("/api/admin/posts/scheduled").with(as(admin)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(scheduled))
                .andExpect(jsonPath("$[0].scheduled").value(true));
        mockMvc.perform(get("/api/admin/posts/scheduled").with(as(learner))).andExpect(status().isForbidden());

        // A scheduled post doesn't exist for viewers (comments, reactions), but admins can preview it.
        mockMvc.perform(get("/api/posts/" + scheduled + "/comments").with(as(learner))).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/posts/" + scheduled + "/reaction").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"LIKE\"}").with(csrf()).with(as(learner))).andExpect(status().isNotFound());

        // Publishing it now moves it into the feed; editing keeps the rest.
        mockMvc.perform(put("/api/admin/posts/" + scheduled).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Now live\",\"pinned\":false,\"publishAt\":\"" + Instant.now().minusSeconds(5) + "\"}")
                        .with(csrf()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Now live"))
                .andExpect(jsonPath("$.scheduled").value(false));
        mockMvc.perform(get("/api/feed").with(as(learner))).andExpect(jsonPath("$.posts.length()").value(3));

        mockMvc.perform(delete("/api/admin/posts/" + older).with(csrf()).with(as(learner))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/posts/" + older).with(csrf()).with(as(admin))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/feed").with(as(learner))).andExpect(jsonPath("$.posts.length()").value(2));
    }

    @Test
    void feedIsPagedTenAtATime() throws Exception {
        for (int i = 0; i < 12; i++) {
            createPost("Post " + i, null, false, null);
        }
        mockMvc.perform(get("/api/feed?page=0").with(as(learner)))
                .andExpect(jsonPath("$.posts.length()").value(10))
                .andExpect(jsonPath("$.hasMore").value(true));
        mockMvc.perform(get("/api/feed?page=1").with(as(learner)))
                .andExpect(jsonPath("$.posts.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void promoPostsLinkToPublishedCoursesAndShowEnrollmentState() throws Exception {
        String draft = createCourse();
        mockMvc.perform(multipart("/api/admin/posts").param("body", "Promo").param("courseId", draft).with(csrf()).with(as(admin)))
                .andExpect(status().isBadRequest());
        publish(draft);
        String promo = createPost("Join our new course!", draft, false, null);

        mockMvc.perform(get("/api/feed").with(as(learner)))
                .andExpect(jsonPath("$.posts[0].id").value(promo))
                .andExpect(jsonPath("$.posts[0].course.id").value(draft))
                .andExpect(jsonPath("$.posts[0].course.title").value("Feed course"))
                .andExpect(jsonPath("$.posts[0].course.enrolled").value(false));
        mockMvc.perform(post("/api/courses/" + draft + "/enroll").with(csrf()).with(as(learner))).andExpect(status().isOk());
        mockMvc.perform(get("/api/feed").with(as(learner))).andExpect(jsonPath("$.posts[0].course.enrolled").value(true));
        mockMvc.perform(get("/api/feed").with(as(other))).andExpect(jsonPath("$.posts[0].course.enrolled").value(false));

        // Un-publishing the course hides the promo card but keeps the post's text.
        mockMvc.perform(post("/api/admin/courses/" + draft + "/unpublish").with(csrf()).with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/api/feed").with(as(learner)))
                .andExpect(jsonPath("$.posts[0].body").value("Join our new course!"))
                .andExpect(jsonPath("$.posts[0].course").doesNotExist());

        // Deleting the course keeps the announcement as plain text.
        courseRepository.deleteAll();
        mockMvc.perform(get("/api/feed").with(as(learner))).andExpect(jsonPath("$.posts.length()").value(1));
    }

    @Test
    void oneReactionPerViewerCanBeChangedAndRemovedAndCountsAreShared() throws Exception {
        String id = createPost("React to me", null, false, null);

        react(id, "LIKE", learner)
                .andExpect(jsonPath("$.myReaction").value("LIKE"))
                .andExpect(jsonPath("$.reactionTotal").value(1));
        react(id, "CELEBRATE", learner)
                .andExpect(jsonPath("$.myReaction").value("CELEBRATE"))
                .andExpect(jsonPath("$.reactionTotal").value(1))
                .andExpect(jsonPath("$.reactions.LIKE").doesNotExist());
        react(id, "CELEBRATE", other)
                .andExpect(jsonPath("$.reactions.CELEBRATE").value(2))
                .andExpect(jsonPath("$.reactionTotal").value(2));
        mockMvc.perform(put("/api/posts/" + id + "/reaction").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"WOW\"}").with(csrf()).with(as(learner))).andExpect(status().is4xxClientError());

        mockMvc.perform(get("/api/feed").with(as(learner)))
                .andExpect(jsonPath("$.posts[0].myReaction").value("CELEBRATE"));
        mockMvc.perform(delete("/api/posts/" + id + "/reaction").with(csrf()).with(as(learner)))
                .andExpect(jsonPath("$.myReaction").doesNotExist())
                .andExpect(jsonPath("$.reactionTotal").value(1));
    }

    @Test
    void commentsAreValidatedAndOnlyTheirAuthorOrAnAdminCanRemoveThem() throws Exception {
        String id = createPost("Discuss", null, false, null);

        mockMvc.perform(post("/api/posts/" + id + "/comments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"  \"}").with(csrf()).with(as(learner))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/posts/" + id + "/comments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"" + "x".repeat(1001) + "\"}").with(csrf()).with(as(learner))).andExpect(status().isBadRequest());

        String mine = json(mockMvc.perform(post("/api/posts/" + id + "/comments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"<b>Great</b> post\"}").with(csrf()).with(as(learner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("Lena Learner"))
                .andExpect(jsonPath("$.body").value("<b>Great</b> post"))
                .andExpect(jsonPath("$.canDelete").value(true))
                .andReturn()).get("id").asText();
        String theirs = json(mockMvc.perform(post("/api/posts/" + id + "/comments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Agreed\"}").with(csrf()).with(as(other))).andReturn()).get("id").asText();

        mockMvc.perform(get("/api/posts/" + id + "/comments").with(as(learner)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].canDelete").value(true))
                .andExpect(jsonPath("$[1].canDelete").value(false));
        mockMvc.perform(get("/api/feed").with(as(learner))).andExpect(jsonPath("$.posts[0].commentCount").value(2));

        mockMvc.perform(delete("/api/comments/" + theirs).with(csrf()).with(as(learner))).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/comments/" + mine).with(csrf()).with(as(learner))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/comments/" + theirs).with(csrf()).with(as(admin))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/posts/" + id + "/comments").with(as(learner))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void postImagesAreValidatedServedAndGoWithThePost() throws Exception {
        MockMultipartHttpServletRequestBuilder bad = multipart("/api/admin/posts");
        bad.file(new MockMultipartFile("image", "evil.png", "image/png", "not an image".getBytes()));
        bad.param("body", "With bad image");
        mockMvc.perform(bad.with(csrf()).with(as(admin))).andExpect(status().is4xxClientError());
        assertThat(postRepository.count()).isZero();

        MockMultipartHttpServletRequestBuilder good = multipart("/api/admin/posts");
        good.file(new MockMultipartFile("image", "pic.png", "image/png", PNG));
        good.param("body", "With image");
        String id = json(mockMvc.perform(good.with(csrf()).with(as(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").exists()).andReturn()).get("id").asText();

        MvcResult image = mockMvc.perform(get("/api/posts/" + id + "/image").with(as(learner))).andExpect(status().isOk()).andReturn();
        assertThat(image.getResponse().getContentType()).isEqualTo("image/png");
        assertThat(image.getResponse().getContentAsByteArray()).isEqualTo(PNG);

        mockMvc.perform(delete("/api/admin/posts/" + id).with(csrf()).with(as(admin))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/posts/" + id + "/image").with(as(learner))).andExpect(status().isNotFound());
    }

    @Test
    void aPostCanCarryAVideoStreamedWithASessionBoundTicketAndNotBothAnImageAndAVideo() throws Exception {
        byte[] mp4 = {0x00, 0x00, 0x00, 0x1C, 'f', 't', 'y', 'p', 'm', 'p', '4', '2',
                0x00, 0x00, 0x00, 0x00, 'm', 'p', '4', '2', 'i', 's', 'o', 'm', 'a', 'v', 'c', '1'};
        MockMultipartHttpServletRequestBuilder both = multipart("/api/admin/posts");
        both.file(new MockMultipartFile("image", "pic.png", "image/png", PNG));
        both.file(new MockMultipartFile("video", "clip.mp4", "video/mp4", mp4));
        both.param("body", "Both");
        mockMvc.perform(both.with(csrf()).with(as(admin))).andExpect(status().isBadRequest());

        MockMultipartHttpServletRequestBuilder fake = multipart("/api/admin/posts");
        fake.file(new MockMultipartFile("video", "clip.mp4", "video/mp4", "not a video".getBytes()));
        fake.param("body", "Fake video");
        mockMvc.perform(fake.with(csrf()).with(as(admin))).andExpect(status().is4xxClientError());
        assertThat(postRepository.count()).isZero();

        MockMultipartHttpServletRequestBuilder good = multipart("/api/admin/posts");
        good.file(new MockMultipartFile("video", "clip.mp4", "video/mp4", mp4));
        good.param("body", "Watch this");
        String id = json(mockMvc.perform(good.with(csrf()).with(as(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").doesNotExist()).andReturn()).get("id").asText();

        MvcResult feed = mockMvc.perform(get("/api/feed").with(as(learner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[0].videoUrl").value(org.hamcrest.Matchers.startsWith("/api/post-stream/"))).andReturn();
        String url = json(feed).get("posts").get(0).get("videoUrl").asText();
        Cookie session = feed.getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();

        mockMvc.perform(get(url).cookie(session).with(as(learner)).header("Range", "bytes=0-3"))
                .andExpect(status().isPartialContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(java.util.Arrays.copyOfRange(mp4, 0, 4)));
        mockMvc.perform(get(url).with(as(learner))).andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/posts/" + id).with(csrf()).with(as(admin))).andExpect(status().isNoContent());
        mockMvc.perform(get(url).cookie(session).with(as(learner))).andExpect(status().isNotFound());
    }

    @Test
    void allSixReactionsWork() throws Exception {
        String id = createPost("Six ways", null, false, null);
        for (String type : List.of("LIKE", "CELEBRATE", "SUPPORT", "LOVE", "INSIGHTFUL", "FUNNY")) {
            react(id, type, learner).andExpect(jsonPath("$.myReaction").value(type));
        }
    }

    private String createPost(String body, String courseId, boolean pinned, Instant publishAt) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/admin/posts");
        request.param("body", body);
        request.param("pinned", String.valueOf(pinned));
        if (courseId != null) {
            request.param("courseId", courseId);
        }
        if (publishAt != null) {
            request.param("publishAt", publishAt.toString());
        }
        return json(mockMvc.perform(request.with(csrf()).with(as(admin))).andExpect(status().isOk()).andReturn()).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions react(String id, String type, User who) throws Exception {
        return mockMvc.perform(put("/api/posts/" + id + "/reaction").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"" + type + "\"}").with(csrf()).with(as(who))).andExpect(status().isOk());
    }

    private String createCourse() throws Exception {
        String course = json(mockMvc.perform(multipart("/api/admin/courses").param("title", "Feed course")
                .with(csrf()).with(as(admin))).andExpect(status().isOk()).andReturn()).get("id").asText();
        String module = json(mockMvc.perform(post("/api/admin/courses/" + course + "/modules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Module\"}").with(csrf()).with(as(admin))).andReturn()).get("id").asText();
        byte[] head = {0x00, 0x00, 0x00, 0x1C, 'f', 't', 'y', 'p', 'm', 'p', '4', '2',
                0x00, 0x00, 0x00, 0x00, 'm', 'p', '4', '2', 'i', 's', 'o', 'm', 'a', 'v', 'c', '1'};
        MockMultipartHttpServletRequestBuilder lesson = multipart("/api/admin/modules/" + module + "/lessons");
        lesson.file(new MockMultipartFile("video", "lecture.mp4", "video/mp4", head));
        lesson.param("title", "Lesson");
        mockMvc.perform(lesson.with(csrf()).with(as(admin))).andExpect(status().isOk());
        return course;
    }

    private void publish(String course) throws Exception {
        mockMvc.perform(post("/api/admin/courses/" + course + "/publish").with(csrf()).with(as(admin))).andExpect(status().isOk());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private User user(String email, String name, Role role) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> userRepository.save(new User(email, name, null, role)));
    }
}
