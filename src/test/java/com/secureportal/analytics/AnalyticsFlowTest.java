package com.secureportal.analytics;

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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.util.List;

import static com.secureportal.testsupport.TestPrincipals.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The admin dashboard's numbers follow what learners actually do. */
@SpringBootTest(properties = {"storage.provider=local", "storage.local-path=target/analytics-test-storage"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class AnalyticsFlowTest {

    private static final String ADMIN = "analytics-admin@example.com";
    private static final String LEARNER = "analytics-learner@example.com";
    private static final byte[] MP4 = {0x00, 0x00, 0x00, 0x1C, 'f', 't', 'y', 'p', 'm', 'p', '4', '2',
            0x00, 0x00, 0x00, 0x00, 'm', 'p', '4', '2', 'i', 's', 'o', 'm', 'a', 'v', 'c', '1'};

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private User admin;
    private User learner;

    @BeforeEach
    void users() {
        admin = userRepository.findByEmailIgnoreCase(ADMIN).orElseGet(() -> userRepository.save(new User(ADMIN, "Admin", null, Role.ADMIN)));
        learner = userRepository.findByEmailIgnoreCase(LEARNER).orElseGet(() -> userRepository.save(new User(LEARNER, "Learner", null, Role.VIEWER)));
    }

    @AfterEach
    void cleanUp() {
        courseRepository.deleteAll();
        for (String email : List.of(ADMIN, LEARNER)) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
    }

    @Test
    void onlyAdminsSeeAnalytics() throws Exception {
        mockMvc.perform(get("/api/admin/analytics").with(as(learner))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/analytics").with(as(admin))).andExpect(status().isOk());
    }

    @Test
    void numbersMoveAsLearnersEnrollFinishLessonsAndPost() throws Exception {
        JsonNode before = analytics();
        assertThat(before.at("/series/dates")).hasSize(30);
        assertThat(before.at("/series/enrollments")).hasSize(30);
        assertThat(before.at("/community/reactionsByType/LIKE").isNumber()).isTrue();

        String course = json(mockMvc.perform(multipart("/api/admin/courses").param("title", "Analytics course").with(csrf()).with(as(admin))).andReturn()).get("id").asText();
        String module = json(mockMvc.perform(post("/api/admin/courses/" + course + "/modules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"M\"}").with(csrf()).with(as(admin))).andReturn()).get("id").asText();
        MockMultipartHttpServletRequestBuilder lesson = multipart("/api/admin/modules/" + module + "/lessons");
        lesson.file(new MockMultipartFile("video", "lecture.mp4", "video/mp4", MP4)).param("title", "L");
        String lessonId = json(mockMvc.perform(lesson.with(csrf()).with(as(admin))).andReturn()).get("id").asText();
        mockMvc.perform(post("/api/admin/courses/" + course + "/publish").with(csrf()).with(as(admin))).andExpect(status().isOk());

        mockMvc.perform(post("/api/courses/" + course + "/enroll").with(csrf()).with(as(learner))).andExpect(status().isOk());
        JsonNode enrolled = analytics();
        assertThat(delta(before, enrolled, "/totals/enrollments")).isEqualTo(1);
        assertThat(delta(before, enrolled, "/funnel/enrolled")).isEqualTo(1);
        assertThat(delta(before, enrolled, "/funnel/started")).isZero();
        assertThat(delta(before, enrolled, "/totals/publishedCourses")).isEqualTo(1);
        assertThat(delta(before, enrolled, "/content/lessons")).isEqualTo(1);
        assertThat(enrolled.at("/series/enrollments/29").asInt()).isGreaterThanOrEqualTo(1);

        mockMvc.perform(put("/api/courses/" + course + "/lessons/" + lessonId + "/progress").contentType(MediaType.APPLICATION_JSON)
                .content("{\"positionSeconds\":5,\"completed\":true}").with(csrf()).with(as(learner))).andExpect(status().isOk());
        JsonNode finished = analytics();
        assertThat(delta(before, finished, "/funnel/started")).isEqualTo(1);
        assertThat(delta(before, finished, "/funnel/completed")).isEqualTo(1);
        assertThat(finished.at("/totals/activeLearners7").asLong()).isGreaterThanOrEqualTo(1);

        JsonNode row = null;
        for (JsonNode candidate : finished.get("topCourses")) {
            if ("Analytics course".equals(candidate.get("title").asText())) {
                row = candidate;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.get("enrollments").asLong()).isEqualTo(1);
        assertThat(row.get("completed").asLong()).isEqualTo(1);

        mockMvc.perform(multipart("/api/posts").param("body", "Hello").with(csrf()).with(as(learner))).andExpect(status().isOk());
        assertThat(delta(before, analytics(), "/community/posts")).isEqualTo(1);
    }

    private JsonNode analytics() throws Exception {
        return json(mockMvc.perform(get("/api/admin/analytics").with(as(admin))).andExpect(status().isOk()).andReturn());
    }

    private static long delta(JsonNode before, JsonNode after, String pointer) {
        return after.at(pointer).asLong() - before.at(pointer).asLong();
    }

    private JsonNode json(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
