package com.secureportal.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.assessment.AttemptRepository;
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
import java.util.UUID;

import static com.secureportal.testsupport.TestPrincipals.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admins run the platform: they can preview courses, but never enroll, take quizzes, track progress or earn certificates. */
@SpringBootTest(properties = {"storage.provider=local", "storage.local-path=target/admin-learner-test-storage"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class AdminNotALearnerTest {

    private static final String ADMIN = "not-a-learner-admin@example.com";
    private static final String LEARNER = "not-a-learner-learner@example.com";
    private static final byte[] MP4 = {0x00, 0x00, 0x00, 0x1C, 'f', 't', 'y', 'p', 'm', 'p', '4', '2',
            0x00, 0x00, 0x00, 0x00, 'm', 'p', '4', '2', 'i', 's', 'o', 'm', 'a', 'v', 'c', '1'};

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    @Autowired
    private LessonProgressRepository progressRepository;
    @Autowired
    private AttemptRepository attemptRepository;
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
    void adminsPreviewCoursesButCannotEnrollTakeQuizzesTrackProgressOrEarnCertificates() throws Exception {
        String course = objectMapper.readTree(mockMvc.perform(multipart("/api/admin/courses").param("title", "Preview course")
                .with(csrf()).with(as(admin))).andReturn().getResponse().getContentAsString()).get("id").asText();
        String module = objectMapper.readTree(mockMvc.perform(post("/api/admin/courses/" + course + "/modules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"M\"}").with(csrf()).with(as(admin))).andReturn().getResponse().getContentAsString()).get("id").asText();
        MockMultipartHttpServletRequestBuilder lesson = multipart("/api/admin/modules/" + module + "/lessons");
        lesson.file(new MockMultipartFile("video", "lecture.mp4", "video/mp4", MP4)).param("title", "L");
        String lessonId = objectMapper.readTree(mockMvc.perform(lesson.with(csrf()).with(as(admin))).andReturn().getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/api/admin/courses/" + course + "/publish").with(csrf()).with(as(admin))).andExpect(status().isOk());

        // Previewing works and is not tracked.
        mockMvc.perform(get("/api/courses/" + course).with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/api/courses/" + course + "/lessons/" + lessonId).with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(put("/api/courses/" + course + "/lessons/" + lessonId + "/progress").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionSeconds\":30,\"completed\":true}").with(csrf()).with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(false));
        assertThat(progressRepository.count()).isZero();

        // Learner things are refused with a clear message.
        mockMvc.perform(post("/api/courses/" + course + "/enroll").with(csrf()).with(as(admin)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value(containsString("don't take them")));
        assertThat(enrollmentRepository.count()).isZero();
        mockMvc.perform(post("/api/assessments/" + UUID.randomUUID() + "/attempts").with(csrf()).with(as(admin))).andExpect(status().isForbidden());
        assertThat(attemptRepository.count()).isZero();
        mockMvc.perform(get("/api/courses/" + course + "/certificate").with(as(admin))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/courses/" + course + "/certificate").with(csrf()).with(as(admin))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/me/learning").with(as(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        // Learners are unaffected.
        mockMvc.perform(post("/api/courses/" + course + "/enroll").with(csrf()).with(as(learner))).andExpect(status().isOk());
        mockMvc.perform(get("/api/me/learning").with(as(learner))).andExpect(jsonPath("$.length()").value(1));
    }
}
