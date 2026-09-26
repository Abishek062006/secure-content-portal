package com.secureportal.course;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Notes are private to the learner who wrote them, only for enrolled learners, and never for admins. */
@SpringBootTest(properties = {"storage.provider=local", "storage.local-path=target/note-test-storage"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class LessonNoteFlowTest {

    private static final String ADMIN = "note-admin@example.com";
    private static final String ONE = "note-learner-one@example.com";
    private static final String TWO = "note-learner-two@example.com";
    private static final byte[] MP4 = {0x00, 0x00, 0x00, 0x1C, 'f', 't', 'y', 'p', 'm', 'p', '4', '2',
            0x00, 0x00, 0x00, 0x00, 'm', 'p', '4', '2', 'i', 's', 'o', 'm', 'a', 'v', 'c', '1'};

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private LessonNoteRepository noteRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private User admin;
    private User one;
    private User two;

    @BeforeEach
    void users() {
        admin = userRepository.findByEmailIgnoreCase(ADMIN).orElseGet(() -> userRepository.save(new User(ADMIN, "Admin", null, Role.ADMIN)));
        one = userRepository.findByEmailIgnoreCase(ONE).orElseGet(() -> userRepository.save(new User(ONE, "One", null, Role.VIEWER)));
        two = userRepository.findByEmailIgnoreCase(TWO).orElseGet(() -> userRepository.save(new User(TWO, "Two", null, Role.VIEWER)));
    }

    @AfterEach
    void cleanUp() {
        courseRepository.deleteAll();
        for (String email : List.of(ADMIN, ONE, TWO)) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
    }

    private String json(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    @Test
    void learnersKeepPrivateTimestampedNotesOnlyOnCoursesTheyAreEnrolledIn() throws Exception {
        String course = objectMapper.readTree(json(mockMvc.perform(multipart("/api/admin/courses").param("title", "Notes course")
                .with(csrf()).with(as(admin))).andReturn())).get("id").asText();
        String module = objectMapper.readTree(json(mockMvc.perform(post("/api/admin/courses/" + course + "/modules")
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"M\"}").with(csrf()).with(as(admin))).andReturn())).get("id").asText();
        MockMultipartHttpServletRequestBuilder lesson = multipart("/api/admin/modules/" + module + "/lessons");
        lesson.file(new MockMultipartFile("video", "lecture.mp4", "video/mp4", MP4)).param("title", "L");
        String lessonId = objectMapper.readTree(json(mockMvc.perform(lesson.with(csrf()).with(as(admin))).andReturn())).get("id").asText();
        mockMvc.perform(post("/api/admin/courses/" + course + "/publish").with(csrf()).with(as(admin))).andExpect(status().isOk());
        String base = "/api/courses/" + course + "/lessons/" + lessonId + "/notes";

        // Not enrolled yet.
        mockMvc.perform(get(base).with(as(one))).andExpect(status().isForbidden());
        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"x\",\"seconds\":1}")
                .with(csrf()).with(as(one))).andExpect(status().isForbidden());

        mockMvc.perform(post("/api/courses/" + course + "/enroll").with(csrf()).with(as(one))).andExpect(status().isOk());
        mockMvc.perform(post("/api/courses/" + course + "/enroll").with(csrf()).with(as(two))).andExpect(status().isOk());

        // Add two notes out of order; they list by moment in the video.
        String later = json(mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"  Remember the loop  \",\"seconds\":95}").with(csrf()).with(as(one)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.body").value("Remember the loop")).andReturn());
        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Intro\",\"seconds\":5}")
                .with(csrf()).with(as(one))).andExpect(status().isCreated());
        mockMvc.perform(get(base).with(as(one))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].seconds").value(5)).andExpect(jsonPath("$[1].seconds").value(95));

        // Validation.
        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"   \",\"seconds\":1}")
                .with(csrf()).with(as(one))).andExpect(status().isBadRequest());
        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"x\",\"seconds\":-3}")
                .with(csrf()).with(as(one))).andExpect(status().isBadRequest());
        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"" + "a".repeat(2001) + "\",\"seconds\":1}").with(csrf()).with(as(one))).andExpect(status().isBadRequest());

        // Another learner sees none of it and can't touch it.
        long noteId = objectMapper.readTree(later).get("id").asLong();
        mockMvc.perform(get(base).with(as(two))).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(put(base + "/" + noteId).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"mine now\"}")
                .with(csrf()).with(as(two))).andExpect(status().isNotFound());
        mockMvc.perform(delete(base + "/" + noteId).with(csrf()).with(as(two))).andExpect(status().isNotFound());

        // The owner edits and deletes.
        mockMvc.perform(put(base + "/" + noteId).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Loops!\"}")
                .with(csrf()).with(as(one))).andExpect(status().isOk()).andExpect(jsonPath("$.body").value("Loops!"));
        mockMvc.perform(delete(base + "/" + noteId).with(csrf()).with(as(one))).andExpect(status().isNoContent());
        mockMvc.perform(get(base).with(as(one))).andExpect(jsonPath("$", hasSize(1)));

        // Admins preview: an empty list, and nothing they can write.
        mockMvc.perform(get(base).with(as(admin))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"x\",\"seconds\":1}")
                .with(csrf()).with(as(admin))).andExpect(status().isForbidden());
    }
}
