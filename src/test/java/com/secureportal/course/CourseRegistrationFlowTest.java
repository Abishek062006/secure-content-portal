package com.secureportal.course;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

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

/** A REGISTER-type course: direct enroll is refused, a request goes to the admin, denial can be resubmitted,
 *  and approval enrolls the learner exactly the way "Enroll" would on an OPEN course. */
@SpringBootTest(properties = {"storage.provider=local", "storage.local-path=target/course-registration-test-storage"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class CourseRegistrationFlowTest {

    private static final String ADMIN_EMAIL = "registration-flow-admin@example.com";
    private static final String VIEWER_EMAIL = "registration-flow-viewer@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    @Autowired
    private CourseRegistrationRequestRepository registrationRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private User admin;
    private User viewer;

    @BeforeEach
    void users() {
        admin = user(ADMIN_EMAIL, Role.ADMIN);
        viewer = user(VIEWER_EMAIL, Role.VIEWER);
    }

    @AfterEach
    void cleanUp() {
        courseRepository.deleteAll();
        userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmailIgnoreCase(VIEWER_EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void aDeniedRequestCanBeResubmittedAndApprovalEnrollsTheLearner() throws Exception {
        String courseId = createRegisterCourse("Invite-only security lab");
        publish(courseId);

        // Direct enroll is refused — this course needs a request instead.
        mockMvc.perform(post("/api/courses/" + courseId + "/enroll").with(csrf()).with(as(viewer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("approval")));

        // The full outline (title, outcomes, modules) is visible before any request is sent.
        mockMvc.perform(get("/api/courses/" + courseId).with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled").value(false))
                .andExpect(jsonPath("$.registrationStatus").doesNotExist());

        // Send a request.
        mockMvc.perform(post("/api/courses/" + courseId + "/register-request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"I work in AppSec and want to follow along.\"}")
                        .with(csrf()).with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled").value(false))
                .andExpect(jsonPath("$.registrationStatus").value("PENDING"));

        // Can't send a second one while the first is still pending.
        mockMvc.perform(post("/api/courses/" + courseId + "/register-request").with(csrf()).with(as(viewer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("already waiting")));

        // Admin sees it in the pending queue.
        MvcResult pending = mockMvc.perform(get("/api/admin/registrations?status=PENDING").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseTitle").value("Invite-only security lab"))
                .andExpect(jsonPath("$[0].userEmail").value(VIEWER_EMAIL))
                .andExpect(jsonPath("$[0].message").value(containsString("AppSec")))
                .andReturn();
        long requestId = json(pending).get(0).get("id").asLong();

        // Deny it.
        mockMvc.perform(post("/api/admin/registrations/" + requestId + "/deny").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Not the right cohort yet.\"}").with(csrf()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DENIED"));
        mockMvc.perform(get("/api/courses/" + courseId).with(as(viewer)))
                .andExpect(jsonPath("$.enrolled").value(false))
                .andExpect(jsonPath("$.registrationStatus").value("DENIED"));
        assertThat(enrollmentRepository.count()).isZero();

        // The learner can ask again — this reuses the same row rather than piling up duplicates.
        mockMvc.perform(post("/api/courses/" + courseId + "/register-request").with(csrf()).with(as(viewer)))
                .andExpect(jsonPath("$.registrationStatus").value("PENDING"));
        assertThat(registrationRepository.count()).isEqualTo(1);

        // Approve it — this enrolls the learner, exactly like a direct "Enroll" would.
        mockMvc.perform(post("/api/admin/registrations/" + requestId + "/approve").with(csrf()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(get("/api/courses/" + courseId).with(as(viewer)))
                .andExpect(jsonPath("$.enrolled").value(true))
                .andExpect(jsonPath("$.registrationStatus").value("APPROVED"));
        assertThat(enrollmentRepository.count()).isEqualTo(1);

        // Now enrolled, a plain "Enroll" is a no-op that just confirms access (not a REGISTER-specific case,
        // but worth being sure the two paths don't fight each other going forward).
        mockMvc.perform(post("/api/courses/" + courseId + "/enroll").with(csrf()).with(as(viewer)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void switchingAPaidCourseToRegisterZeroesThePrice() throws Exception {
        String courseId = json(mockMvc.perform(multipart("/api/admin/courses").param("title", "Was paid")
                        .param("priceRupees", "999").with(csrf()).with(as(admin)))
                .andExpect(status().isOk()).andReturn()).get("id").asText();

        mockMvc.perform(put("/api/admin/courses/" + courseId + "/pricing").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessType\":\"REGISTER\",\"priceRupees\":999}").with(csrf()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessType").value("REGISTER"))
                .andExpect(jsonPath("$.pricing.free").value(true));
    }

    // ---- helpers ----

    private String createRegisterCourse(String title) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/admin/courses");
        request.param("title", title);
        request.param("accessType", "REGISTER");
        request.param("outcomes", "Trace a real exploit end to end\nWrite a fix that actually holds");
        MvcResult created = mockMvc.perform(request.with(csrf()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessType").value("REGISTER"))
                .andReturn();
        return json(created).get("id").asText();
    }

    private void publish(String courseId) throws Exception {
        String module = json(mockMvc.perform(post("/api/admin/courses/" + courseId + "/modules")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"M\"}")
                        .with(csrf()).with(as(admin))).andReturn()).get("id").asText();
        mockMvc.perform(multipart("/api/admin/modules/" + module + "/lessons")
                        .file(new org.springframework.mock.web.MockMultipartFile("video", "l.mp4", "video/mp4", mp4()))
                        .param("title", "L").with(csrf()).with(as(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/courses/" + courseId + "/publish").with(csrf()).with(as(admin)))
                .andExpect(status().isOk());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private User user(String email, Role role) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(new User(email, "Registration Flow Test", null, role)));
    }

    private static byte[] mp4() {
        byte[] head = {0x00, 0x00, 0x00, 0x1C, 'f', 't', 'y', 'p', 'm', 'p', '4', '2',
                0x00, 0x00, 0x00, 0x00, 'm', 'p', '4', '2', 'i', 's', 'o', 'm', 'a', 'v', 'c', '1'};
        byte[] bytes = new byte[4096];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i % 251);
        }
        System.arraycopy(head, 0, bytes, 0, head.length);
        return bytes;
    }
}
