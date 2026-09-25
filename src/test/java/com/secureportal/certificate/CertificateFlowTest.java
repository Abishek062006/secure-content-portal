package com.secureportal.certificate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.assessment.Attempt;
import com.secureportal.assessment.AttemptRepository;
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

import java.util.List;

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

/** Earning, issuing, downloading and verifying certificates, and the "My learning" list. */
@SpringBootTest(properties = {"storage.provider=local", "storage.local-path=target/certificate-test-storage"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class CertificateFlowTest {

    private static final String ADMIN = "cert-flow-admin@example.com";
    private static final String LEARNER = "cert-flow-learner@example.com";
    private static final String OTHER = "cert-flow-other@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private AttemptRepository attemptRepository;
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
        courseRepository.deleteAll();
        for (String email : List.of(ADMIN, LEARNER, OTHER)) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
    }

    @Test
    void certificateNeedsEveryLessonAndEveryGradedAssessmentThenIsIssuedOnceDownloadedAndVerifiable() throws Exception {
        String course = json(mockMvc.perform(multipart("/api/admin/courses").param("title", "Certified course")
                .with(csrf()).with(as(admin))).andExpect(status().isOk()).andReturn()).get("id").asText();
        String module = json(mockMvc.perform(post("/api/admin/courses/" + course + "/modules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Only module\"}").with(csrf()).with(as(admin))).andReturn()).get("id").asText();
        String lesson = addLesson(module);
        mockMvc.perform(post("/api/admin/lessons/" + lesson + "/questions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Which?\",\"difficulty\":\"EASY\",\"explanation\":\"Because.\","
                                + "\"options\":[\"A\",\"B\",\"C\",\"D\"],\"correctIndex\":0}").with(csrf()).with(as(admin)))
                .andExpect(status().isOk());
        String assessment = json(mockMvc.perform(put("/api/admin/courses/" + course + "/final-assessment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ASSESSMENT\",\"title\":\"Final exam\",\"easyCount\":1,\"mediumCount\":0,\"hardCount\":0,"
                                + "\"passPercent\":50,\"gatesNext\":false}").with(csrf()).with(as(admin)))
                .andExpect(status().isOk()).andReturn()).get("id").asText();
        mockMvc.perform(post("/api/admin/courses/" + course + "/publish").with(csrf()).with(as(admin))).andExpect(status().isOk());

        // Not enrolled: no certificate status, and not on My learning.
        mockMvc.perform(get("/api/courses/" + course + "/certificate").with(as(learner))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/me/learning").with(as(learner))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/courses/" + course + "/enroll").with(csrf()).with(as(learner))).andExpect(status().isOk());
        mockMvc.perform(get("/api/courses/" + course + "/certificate").with(as(learner)))
                .andExpect(jsonPath("$.earned").value(false))
                .andExpect(jsonPath("$.missing.length()").value(2))
                .andExpect(jsonPath("$.missing[0]").value(containsString("lesson")))
                .andExpect(jsonPath("$.missing[1]").value(containsString("Final exam")));
        mockMvc.perform(post("/api/courses/" + course + "/certificate").with(csrf()).with(as(learner)))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/courses/" + course + "/lessons/" + lesson + "/progress").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionSeconds\":5,\"completed\":true}").with(csrf()).with(as(learner)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/courses/" + course + "/certificate").with(as(learner)))
                .andExpect(jsonPath("$.missing.length()").value(1))
                .andExpect(jsonPath("$.missing[0]").value(containsString("Final exam")));

        // A failed attempt doesn't count; a passed one does.
        Attempt failed = new Attempt(java.util.UUID.fromString(assessment), learner.getId(), 1, null);
        failed.grade(0, 1, 0, false, false);
        attemptRepository.save(failed);
        mockMvc.perform(post("/api/courses/" + course + "/certificate").with(csrf()).with(as(learner))).andExpect(status().isConflict());
        Attempt passed = new Attempt(java.util.UUID.fromString(assessment), learner.getId(), 1, null);
        passed.grade(1, 1, 100, true, false);
        attemptRepository.save(passed);

        mockMvc.perform(get("/api/courses/" + course + "/certificate").with(as(learner)))
                .andExpect(jsonPath("$.earned").value(true))
                .andExpect(jsonPath("$.certificate").doesNotExist());
        JsonNode issued = json(mockMvc.perform(post("/api/courses/" + course + "/certificate").with(csrf()).with(as(learner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientName").value("Lena Learner"))
                .andExpect(jsonPath("$.courseTitle").value("Certified course"))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}")))
                .andReturn());
        mockMvc.perform(post("/api/courses/" + course + "/certificate").with(csrf()).with(as(learner)))
                .andExpect(jsonPath("$.id").value(issued.get("id").asText()));

        mockMvc.perform(get("/api/me/learning").with(as(learner)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].course.title").value("Certified course"))
                .andExpect(jsonPath("$[0].course.progressPercent").value(100))
                .andExpect(jsonPath("$[0].resumeLessonId").value(lesson))
                .andExpect(jsonPath("$[0].certificate.code").value(issued.get("code").asText()));

        MvcResult pdf = mockMvc.perform(get("/api/certificates/" + issued.get("id").asText() + "/pdf").with(as(learner)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(pdf.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 5)).isEqualTo("%PDF-");
        mockMvc.perform(get("/api/certificates/" + issued.get("id").asText() + "/pdf").with(as(other))).andExpect(status().isNotFound());

        // Verification is public and forgiving about case; unknown codes are simply invalid.
        mockMvc.perform(get("/api/certificates/verify/" + issued.get("code").asText().toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.recipientName").value("Lena Learner"));
        mockMvc.perform(get("/api/certificates/verify/AAAA-BBBB-CCCC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void codesLookRightAndTheRendererSurvivesNamesTheBuiltInFontCannotDraw() {
        assertThat(CertificateService.newCode()).matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}");
        Certificate certificate = new Certificate("ABCD-EFGH-JKLM", 1L, java.util.UUID.randomUUID(),
                "张伟 Zhang — " + "Very Long Name ".repeat(12), "A course with a very long title ".repeat(6));
        byte[] pdf = new CertificatePdfRenderer().render(certificate);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    private String addLesson(String moduleId) throws Exception {
        byte[] head = {0x00, 0x00, 0x00, 0x1C, 'f', 't', 'y', 'p', 'm', 'p', '4', '2',
                0x00, 0x00, 0x00, 0x00, 'm', 'p', '4', '2', 'i', 's', 'o', 'm', 'a', 'v', 'c', '1'};
        MockMultipartHttpServletRequestBuilder lesson = multipart("/api/admin/modules/" + moduleId + "/lessons");
        lesson.file(new MockMultipartFile("video", "lecture.mp4", "video/mp4", head));
        lesson.param("title", "The lesson");
        return json(mockMvc.perform(lesson.with(csrf()).with(as(admin))).andExpect(status().isOk()).andReturn()).get("id").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private User user(String email, String name, Role role) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> userRepository.save(new User(email, name, null, role)));
    }
}
